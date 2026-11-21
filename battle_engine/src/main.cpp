#include "Snapshot.h"
#include <iostream>
#include <sstream>
#ifdef _WIN32
#include <windows.h>
#endif

int main(int argc,char **argv) {
#ifdef _WIN32
    SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX);
#endif
    bool serve=argc==2 && std::string(argv[1])=="--serve";
    bridge::Session session;
    auto process=[&](const bridge::Json &request) {
        bridge::Json response;
        auto *output=std::cout.rdbuf();std::cout.rdbuf(std::cerr.rdbuf());
        try {response=session.search(request);}
        catch(const std::exception &e) {response={{"supported",false},{"reason",e.what()}};}
        std::cout.rdbuf(output);
        if(request.contains("request_id"))response["request_id"]=request["request_id"];
        std::cout << response.dump(-1,' ',true) << '\n' << std::flush;
    };
    if(serve) {
        std::string line;
        while(std::getline(std::cin,line)) {
            try {process(bridge::Json::parse(line));}
            catch(const std::exception &e) {std::cout << bridge::Json({{"supported",false},{"reason",e.what()}}).dump()<<'\n'<<std::flush;}
        }
    } else {
        try {bridge::Json request;std::cin>>request;process(request);}
        catch(const std::exception &e) {std::cout<<bridge::Json({{"supported",false},{"reason",e.what()}}).dump()<<'\n';}
    }
    return 0;
}
