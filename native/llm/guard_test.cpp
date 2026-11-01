#include "generation_guard.h"
#include <cassert>
#include <iostream>
int main() {
    std::vector<int32_t> natural;for(int i=0;i<384;i++)natural.push_back((i*37+i/19)%1009);
    assert(!pocket_guard::repeating(natural));
    assert(!pocket_guard::repeating(std::vector<int32_t>(8,3)));
    assert(pocket_guard::repeating(std::vector<int32_t>(12,3)));
    std::vector<int32_t> cycle;for(int copy=0;copy<4;copy++)for(int i=0;i<48;i++)cycle.push_back(i+100);
    assert(pocket_guard::repeating(cycle));
    auto started=pocket_guard::milliseconds();assert(pocket_guard::milliseconds()>=started);
    std::cout<<"PASS: normal output, short repetitions, stuck token and long-cycle loop guards\n";
}
