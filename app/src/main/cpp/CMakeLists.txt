cmake_minimum_required(VERSION 3.22.1)

project("lunartag_native")

add_library(
    lunartag_native
    SHARED
    native-lib.cpp
)

target_compile_options(
    lunartag_native
    PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

target_link_options(
    lunartag_native
    PRIVATE
    "-Wl,--gc-sections"
    "-Wl,-z,max-page-size=16384"
    $<$<CONFIG:Release>:-s>
)