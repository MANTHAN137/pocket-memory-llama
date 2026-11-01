package com.pocketmemory.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A narrow Streamable HTTP MCP 2025-11-25 client for Exa's read-only search tool. */
final class McpClient implements AutoCloseable {
    static final String ENDPOINT="https://mcp.exa.ai/mcp?tools=web_search_exa";
    final WebGuard guard;
    final URL endpoint;
    volatile HttpURLConnection connection;
    String version="2025-11-25",session;
    long nextId=0;
    McpClient(WebGuard guard)throws Exception{this.guard=guard;endpoint=new URL(ENDPOINT);}
    void cancel(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    @Override public void close(){cancel();}
    JSONObject request(String method,JSONObject params)throws Exception{
        long id=++nextId;JSONObject message=new JSONObject().put("jsonrpc","2.0").put("id",id).put("method",method).put("params",params);
        JSONObject response=post(message,id);
        if(response==null||!response.has("result")||response.has("error"))throw new IOException("The web service could not complete this request.");
        return response.getJSONObject("result");
    }
    void connect()throws Exception{
        JSONObject result=request("initialize",new JSONObject().put("protocolVersion",version).put("capabilities",new JSONObject()).put("clientInfo",new JSONObject().put("name","PocketMemory-Llama").put("version","0.7.0")));
        String selected=result.optString("protocolVersion");if(!Set.of("2025-11-25","2025-06-18").contains(selected)||!result.getJSONObject("capabilities").has("tools"))throw new IOException("The web service is not compatible.");version=selected;
        post(new JSONObject().put("jsonrpc","2.0").put("method","notifications/initialized"),null);
        JSONObject list=request("tools/list",new JSONObject());JSONArray tools=list.getJSONArray("tools");if(tools.length()>32||list.has("nextCursor"))throw new IOException("Unexpected web tool list.");
        JSONObject schema=null;
        for(int i=0;i<tools.length();i++){JSONObject tool=tools.getJSONObject(i);if(tool.optString("name").equals("web_search_exa")){if(schema!=null)throw new IOException("Duplicate web tool.");schema=tool.getJSONObject("inputSchema");}}
        if(schema==null)throw new IOException("Web search is unavailable.");
        JSONObject props=schema.getJSONObject("properties");if(!"object".equals(schema.optString("type"))||!"string".equals(props.getJSONObject("query").optString("type"))||!"string".equals(props.getJSONObject("objective").optString("type")))throw new IOException("The web tool format changed.");
        JSONArray required=schema.optJSONArray("required");if(required!=null)for(int i=0;i<required.length();i++)if(!Set.of("query","objective","numResults").contains(required.getString(i)))throw new IOException("The web tool needs an unsupported field.");
    }
    String search(String question)throws Exception{
        guard.tool("web_search_exa",question);
        JSONObject args=new JSONObject().put("query",question).put("objective","Find reliable pages answering this question. Prefer primary sources; return factual excerpts. Question: "+question).put("numResults",3);
        JSONObject result=request("tools/call",new JSONObject().put("name","web_search_exa").put("arguments",args));
        if(result.optBoolean("isError"))throw new IOException("Search is unavailable or busy. Try again later.");
        JSONArray content=result.getJSONArray("content");if(content.length()>16)throw new IOException("Too many web result blocks.");StringBuilder text=new StringBuilder();
        for(int i=0;i<content.length();i++){JSONObject block=content.getJSONObject(i);if(block.optString("type").equals("text"))text.append(block.getString("text")).append('\n');}
        if(text.length()==0||text.length()>60000)throw new IOException("The web service returned no usable results.");return text.toString();
    }
    JSONObject post(JSONObject body,Long expected)throws Exception{
        guard.request();HttpURLConnection c=(HttpURLConnection)endpoint.openConnection();connection=c;
        try{
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(Math.min(10000,guard.remaining()));c.setReadTimeout(Math.min(15000,guard.remaining()));c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json, text/event-stream");c.setRequestProperty("MCP-Protocol-Version",version);c.setRequestProperty("User-Agent","PocketMemory-Llama/0.7.0");if(session!=null)c.setRequestProperty("Mcp-Session-Id",session);
            byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}
            guard.check();int status=c.getResponseCode();if(status==429)throw new IOException("Web search is busy. Try again later.");if(status<200||status>=300)throw new IOException("Could not reach web search. Check your connection.");
            String assigned=c.getHeaderField("Mcp-Session-Id");if(assigned!=null){if(assigned.length()>512||!assigned.matches("[\\x20-\\x7E]+"))throw new IOException("Invalid web session.");session=assigned;}
            if(expected==null){if(status!=202&&status!=204)throw new IOException("Unexpected MCP notification response.");return null;}
            String type=c.getContentType();if(type==null)throw new IOException("Missing MCP response type.");
            try(InputStream raw=new BudgetStream(c.getInputStream(),guard)){
                if(type.toLowerCase(Locale.ROOT).startsWith("application/json")){String json=new String(raw.readAllBytes(),StandardCharsets.UTF_8);JSONObject value=new JSONObject(json);validate(value,expected);return value;}
                if(!type.toLowerCase(Locale.ROOT).startsWith("text/event-stream"))throw new IOException("Unsupported MCP response type.");
                BufferedReader reader=new BufferedReader(new InputStreamReader(raw,StandardCharsets.UTF_8));String line;StringBuilder event=new StringBuilder();int events=0;
                while((line=reader.readLine())!=null){guard.check();if(line.isEmpty()){
                    if(++events>32)throw new IOException("Too many MCP events.");
                    if(event.length()>0){JSONObject value=new JSONObject(event.toString());event.setLength(0);if(value.has("method")&&value.has("id"))throw new IOException("Server-initiated actions are not allowed.");if(value.has("id")){validate(value,expected);return value;}}
                }else if(line.startsWith("data:")){if(event.length()>0)event.append('\n');event.append(line.substring(5).stripLeading());}}
                throw new IOException("The web response ended early.");
            }
        }catch(UnknownHostException e){throw new IOException("Couldn't connect. Check your internet and try again.",e);
        }catch(SocketTimeoutException e){throw new IOException("Web search timed out. Try again.",e);
        }catch(javax.net.ssl.SSLException e){throw new IOException("Couldn't connect securely. Try again later.",e);
        }finally{c.disconnect();if(connection==c)connection=null;}
    }
    static void validate(JSONObject value,long expected)throws Exception{if(!"2.0".equals(value.optString("jsonrpc"))||value.has("method")||value.optLong("id",-1)!=expected)throw new IOException("Unexpected MCP response.");}
    static final class BudgetStream extends FilterInputStream {
        final WebGuard guard;BudgetStream(InputStream in,WebGuard guard){super(in);this.guard=guard;}
        @Override public int read()throws IOException{guard.check();int n=super.read();if(n>=0)guard.bytes(1);return n;}
        @Override public int read(byte[] b,int off,int len)throws IOException{guard.check();int n=in.read(b,off,len);if(n>0)guard.bytes(n);return n;}
    }
}
