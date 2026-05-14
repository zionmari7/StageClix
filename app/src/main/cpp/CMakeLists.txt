cmake_minimum_required(VERSION 3.22.1)
project(StageClix)

find_package(oboe REQUIRED CONFIG)

add_library(stageclix_engine SHARED
    AudioEngine.cpp
    VoiceCuePlayer.cpp
    JniBridge.cpp
    native-lib.cpp
)

target_link_libraries(stageclix_engine
    android
    log
    oboe::oboe
)

# AGP does not reliably package shared Prefab libraries; copy liboboe.so
# into the CMake output dir so it lands in the APK's lib/<abi>/ folder.
get_target_property(OBOE_LIB_PATH oboe::oboe IMPORTED_LOCATION)
add_custom_command(
    TARGET stageclix_engine POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${OBOE_LIB_PATH}"
        "$<TARGET_FILE_DIR:stageclix_engine>/liboboe.so"
)
