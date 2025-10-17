cmake_minimum_required(VERSION 3.4.1)
add_library(directbuf SHARED src/main/cpp/directbuf.cpp)
target_link_libraries(directbuf android log)
