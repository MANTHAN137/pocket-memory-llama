'use strict';
(() => {
  let bearer='',activeJob='',busy=false;
  const el=id=>document.getElementById(id);
  const registration=new AbortController();
  async function api(path,body,signal){
    const response=await fetch(path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(bearer?{Authorization:'Bearer '+bearer}:{})},body:body===undefined?undefined:JSON.stringify(body),signal,cache:'no-store',credentials:'omit'});
    const value=await response.json();if(!response.ok)throw new Error(value.error||'This request could not be completed.');return value;
  }
  function message(text){el('connection').textContent=text;}
  function wait(signal){return new Promise((resolve,reject)=>{let abort;const timer=setTimeout(()=>{if(signal)signal.removeEventListener('abort',abort);resolve();},2000);abort=()=>{clearTimeout(timer);reject(new DOMException('Stopped','AbortError'));};if(signal)signal.addEventListener('abort',abort,{once:true});});}
  function controls(on){busy=on;el('send').disabled=on||!bearer;el('search').disabled=on||!bearer;el('stop').hidden=!on;el('question').disabled=on;el('mode').disabled=on;}
  function showAnswer(value){el('answer-panel').hidden=false;el('answer').textContent=value.answer||'';el('sources').replaceChildren();for(const source of value.sources||[]){const article=document.createElement('article'),title=document.createElement('h3'),body=document.createElement('p');title.textContent=source.title;body.textContent=source.text;article.append(title,body);el('sources').append(article);}}
  async function run(kind,input,signal){
    if(busy)throw new Error('A reply is already running.');if(!bearer)throw new Error('Open the companion from the Android app.');
    controls(true);el('mode').value=kind;el('question').value=input;message(kind==='search_web'?'Searching web sources…':'Llama is working…');
    let abort;
    try{
      const job=await api('/api/jobs',{kind,input},signal);activeJob=job.id;
      abort=()=>{api('/api/stop',{id:job.id}).catch(()=>{});};if(signal)signal.addEventListener('abort',abort,{once:true});
      for(let step=0;step<95;step++){
        if(signal?.aborted)throw new DOMException('Stopped','AbortError');
        const state=await api('/api/jobs/'+encodeURIComponent(job.id),undefined,signal);
        if(state.state==='complete'){showAnswer(state.result);message('Connected to Pocket Memory');return state.result;}
        if(state.state==='error')throw new Error(state.error||'The reply stopped.');
        await wait(signal);
      }
      await api('/api/stop',{id:job.id});throw new Error('The reply reached its time limit.');
    }finally{if(signal&&abort)signal.removeEventListener('abort',abort);activeJob='';controls(false);}
  }
  async function search(query,signal){if(busy)throw new Error('Wait for the reply to finish.');const value=await api('/api/memories',{query},signal);el('query').value=query;el('memories').replaceChildren();for(const note of value.memories){const article=document.createElement('article'),title=document.createElement('h3'),body=document.createElement('p');title.textContent=note.title;body.textContent=note.text;article.append(title,body);el('memories').append(article);}if(!value.memories.length){const text=document.createElement('p');text.textContent='No matching memories.';el('memories').append(text);}return value;}
  el('ask').addEventListener('submit',event=>{event.preventDefault();run(el('mode').value,el('question').value.trim()).catch(error=>message(error.name==='AbortError'?'Reply stopped.':error.message));});
  el('find').addEventListener('submit',event=>{event.preventDefault();search(el('query').value.trim()).catch(error=>message(error.message));});
  el('stop').addEventListener('click',()=>{if(activeJob)api('/api/stop',{id:activeJob}).then(()=>message('Stopping…')).catch(error=>message(error.message));});
  el('mode').addEventListener('change',()=>{el('privacy').textContent=el('mode').value==='search_web'?'Only this question is sent to Exa. Saved memories are not attached.':'Inference stays on your phone.';});
  async function register(){
    const context=document.modelContext||navigator.modelContext;
    if(!context?.registerTool){el('webmcp').textContent='WebMCP is unavailable in this browser. The buttons still work.';return;}
    const tools=[
      {name:'get_model_status',description:'Check which local Llama model is ready in Pocket Memory.',inputSchema:{type:'object',properties:{},additionalProperties:false},execute:()=>api('/api/model'),annotations:{readOnlyHint:true}},
      {name:'search_memories',description:'Find saved memories in the phone’s Pocket Memory app.',inputSchema:{type:'object',properties:{query:{type:'string',maxLength:120,description:'Text to find in saved memories.'}},required:['query'],additionalProperties:false},execute:(input,options)=>search(input.query,options?.signal),annotations:{readOnlyHint:true}},
      ...[['ask_memory','Answer a question using the phone’s saved memories.'],['chat_with_llama','Send a message to the local Llama model.'],['search_web','Search the web through one bounded MCP call and answer with local Llama.']].map(([name,description])=>({name,description,inputSchema:{type:'object',properties:{question:{type:'string',minLength:1,maxLength:name==='search_web'?300:600,description:'The question to answer.'}},required:['question'],additionalProperties:false},execute:(input,options)=>run(name,input.question,options?.signal),annotations:{readOnlyHint:false}}))
    ];
    for(const tool of tools)await context.registerTool(tool,{signal:registration.signal});el('webmcp').textContent='WebMCP ready · '+tools.length+' tools';
  }
  async function connect(){
    const code=location.hash.slice(1);history.replaceState(null,'',location.pathname);if(!code){message('Open this companion from Pocket Memory on your phone.');return;}
    try{const session=await api('/session',{code});bearer=session.session;const model=await api('/api/model');el('model').textContent=model.name;message(model.ready?'Connected to Pocket Memory':'Download Llama in the Android app to ask questions.');controls(false);await register();}
    catch(error){message(error.message);}
  }
  window.addEventListener('pagehide',()=>registration.abort(),{once:true});connect();
})();
