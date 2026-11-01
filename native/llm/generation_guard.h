#pragma once
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <vector>

namespace pocket_guard {
inline int64_t milliseconds() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
// Stop repeated token cycles, including loops much longer than one repeated word.
inline bool repeating(const std::vector<int32_t> & tokens) {
    const size_t n=tokens.size();
    for(size_t period=1;period<=64;period++) {
        size_t copies=period==1?12:period<4?8:4;
        if(n<period*copies)continue;
        bool repeated=true;
        for(size_t k=1;k<copies&&repeated;k++)
            repeated=std::equal(tokens.end()-period,tokens.end(),tokens.end()-period*(k+1));
        if(repeated)return true;
    }
    return false;
}
}
