cmake_minimum_required(VERSION 3.19)
project(
        sts_lightspeed
        VERSION 0.1
        DESCRIPTION "High Performance, rng accurate implementation of Slay the Spire with tools for tree search and ml."
        LANGUAGES CXX)

file (GLOB_RECURSE sts_lightspeed_SOURCES CONFIGURE_DEPENDS "src/*.cpp")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_executable(main apps/main.cpp ${sts_lightspeed_SOURCES})
add_executable(battle-sim apps/battle-sim.cpp ${sts_lightspeed_SOURCES})
add_executable(card-reward-eval apps/card-reward-eval.cpp ${sts_lightspeed_SOURCES})
add_executable(battle-agent apps/battle-agent.cpp ${sts_lightspeed_SOURCES})
add_executable(map-render apps/map-render.cpp src/game/Map.cpp)
add_executable(test apps/test.cpp ${sts_lightspeed_SOURCES})
add_executable(defect-converter-test apps/defect-converter-test.cpp ${sts_lightspeed_SOURCES})
add_executable(defect-combat-test apps/defect-combat-test.cpp ${sts_lightspeed_SOURCES})

foreach(target main battle-sim card-reward-eval battle-agent map-render test
        defect-converter-test defect-combat-test)
    target_include_directories(${target} PRIVATE include)
    target_include_directories(${target} SYSTEM PRIVATE json/include)
endforeach()
