#pragma once
#include "Snapshot.h"
#include <cctype>
#include <iomanip>
#include <sstream>

namespace bridge {
inline const char *headlessPolicy() { return "headless-b354a9ee-v1"; }
inline std::uint64_t rngWord(const Json &value) {
    if(!value.is_string())throw Unsupported("headless_rng_word_type");
    const auto word=value.get<std::string>();
    if(word.size()!=16 || word.find_first_not_of("0123456789abcdef")!=std::string::npos)
        throw Unsupported("headless_rng_word_encoding");
    return std::stoull(word,nullptr,16);
}
inline void validateHeadlessRng(const Json &payload) {
    if(!payload.is_object() || payload.value("schema",std::string())!="headless-rng-v1" ||
       payload.value("policy",std::string())!=headlessPolicy())throw Unsupported("headless_rng_policy");
    const auto &streams=payload.at("streams");
    const char *names[]={"monster","map","event","merchant","card","treasure","relic","potion",
        "monster_hp","ai","shuffle","card_random","misc","headless"};
    if(!streams.is_object() || streams.size()!=14)throw Unsupported("headless_rng_streams");
    for(auto name:names) {
        const auto &r=streams.at(name);
        auto a=rngWord(r.at("state0")),b=rngWord(r.at("state1"));
        if(!(a|b))throw Unsupported("headless_rng_zero_state");
        if(!r.at("counter").is_number_integer() || r.at("counter").get<long long>()<0 ||
            r.at("counter").get<long long>()>10000000)throw Unsupported("headless_rng_counter");
    }
    const auto &math=payload.at("math");
    if(!(rngWord(math.at("state0"))|rngWord(math.at("state1"))))throw Unsupported("headless_rng_zero_state");
}
inline sts::Random exactRandom(const Json &payload,const char *name,int expectedCounter) {
    const auto &r=payload.at("streams").at(name);
    if(r.at("counter")!=expectedCounter)throw Unsupported(std::string("headless_rng_counter_mismatch:")+name);
    sts::Random out;out.seed0=rngWord(r.at("state0"));out.seed1=rngWord(r.at("state1"));out.counter=expectedCounter;
    return out;
}
inline std::string rngHex(std::uint64_t value) {
    std::ostringstream out;out<<std::hex<<std::setfill('0')<<std::setw(16)<<value;return out.str();
}
inline Json exactRngView(const sts::BattleContext &b) {
    Json out=Json::object();
    auto put=[&](const char *name,const sts::Random &r){out[name]={{"state0",rngHex(r.seed0)},{"state1",rngHex(r.seed1)},{"counter",r.counter}};};
    put("ai",b.aiRng);put("card_random",b.cardRandomRng);put("misc",b.miscRng);put("monster_hp",b.monsterHpRng);
    put("potion",b.potionRng);put("shuffle",b.shuffleRng);if(b.cardRngKnown)put("card",b.cardRng);
    if(b.headlessRngKnown)put("headless",b.headlessRng);
    return out;
}
}
