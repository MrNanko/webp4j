cmake_minimum_required(VERSION 3.15)
project(webp4j VERSION 1.0.0 LANGUAGES C CXX)

# ============================================
# Build Options
# ============================================

  # Set C/C++ standard
  set(CMAKE_C_STANDARD 11)
  set(CMAKE_C_STANDARD_REQUIRED ON)
  set(CMAKE_CXX_STANDARD 11)
  set(CMAKE_CXX_STANDARD_REQUIRED ON)

  # Set output directories
  set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
  set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

  # Default to Release build type
  if(NOT CMAKE_BUILD_TYPE)
      set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
  endif()
  message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

  # Find JNI
  find_package(JNI REQUIRED)
  include_directories(${JNI_INCLUDE_DIRS})

  # Find or add libwebp
  find_package(WebP)
  if(NOT WebP_FOUND)
      # If system doesn't have libwebp, use bundled version from subdirectory
      message(STATUS "libwebp not found, using bundled version from libwebp/")

      # Disable libwebp example tools build, only build libraries
      set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)

      # Conditionally disable CPU-specific SIMD optimizations for QEMU build environments
      # QEMU has known issues when compiling architecture-specific SIMD instructions:
      # - SSE/AVX (x86_64)
      # - NEON (ARM)
      # - MSA (MIPS)
      # Set WEBP4J_QEMU_BUILD=1 when building under QEMU to avoid compilation segfaults
      if(DEFINED ENV{WEBP4J_QEMU_BUILD} AND "$ENV{WEBP4J_QEMU_BUILD}" STREQUAL "1")
          message(STATUS "SIMD optimizations: DISABLED (building under QEMU, avoiding SIMD compilation issues)")
          set(WEBP_ENABLE_SIMD OFF CACHE BOOL "" FORCE)
          set(WEBP_BUILD_NEON OFF CACHE BOOL "" FORCE)
      else()
          message(STATUS "SIMD optimizations: ENABLED (default)")
      endif()

      # Force static library compilation (Important: ensure .a/.lib files are generated instead of .so/.dylib/.dll)
      set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

      # Enable PIC (Position Independent Code) on Unix-like systems
      # This is required for linking static libraries into shared libraries
      # Windows doesn't need -fPIC, MSVC and MinGW handle it automatically
      if(UNIX)
          set(CMAKE_POSITION_INDEPENDENT_CODE ON)
      endif()

      # Windows specific configuration
      if(WIN32)
          # Ensure MSVC can properly link static libraries to DLL
          if(MSVC)
              # MSVC needs /MT or /MD to control runtime library linking
              # Use /MD to support dynamic runtime (recommended)
              set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")
          endif()
      endif()

      # Add libwebp subdirectory (exclude from "make install" to prevent system-wide installation)
      # We only need to build libwebp statically and link it into our webp4j library
      add_subdirectory(libwebp EXCLUDE_FROM_ALL)
  endif()

  # Add header file search paths
  include_directories(
      ${CMAKE_CURRENT_SOURCE_DIR}/src/main/c
      ${CMAKE_CURRENT_SOURCE_DIR}/libwebp/src
      ${CMAKE_CURRENT_BINARY_DIR}/libwebp/src
  )

  # Source files
  set(SOURCES
      src/main/c/webp4j.c
      # Add other source files
  )

  # Create shared library
  add_library(webp4j SHARED ${SOURCES})

  # Link libraries
  # - webp (main library)
  # - sharpyuv (libwebp dependency)
  # - webpmux and webpdemux (optional, but recommended for full functionality)
  target_link_libraries(webp4j PRIVATE
      ${JNI_LIBRARIES}
      webp
      sharpyuv
  )

  # On Linux, use --whole-archive to ensure all symbols are included
  if(UNIX AND NOT APPLE)
      # Add math library (corresponds to -lm in manual compilation)
      target_link_libraries(webp4j PRIVATE m)
  endif()

  # ============================================
  # Compiler Optimization Options
  # ============================================

  # Set compiler warnings and optimization options
  if(MSVC)
      # MSVC compiler options
      target_compile_options(webp4j PRIVATE
          /W4                 # Warning level 4
          /WX-                # Warnings not as errors
          /O2                 # Release optimization
      )
  else()
      # GCC/Clang compiler options
      target_compile_options(webp4j PRIVATE
          -Wall               # Enable all warnings
          -Wextra             # Enable extra warnings
          -pedantic           # Strict standard compliance
          $<$<CONFIG:Release>:-O3>            # Release mode: maximum optimization
          $<$<CONFIG:Release>:-DNDEBUG>       # Release mode: disable assertions
          $<$<CONFIG:Debug>:-g>               # Debug mode: debug info
          $<$<CONFIG:Debug>:-O0>              # Debug mode: no optimization
      )

      # Link-time optimization (LTO)
      if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
          include(CheckIPOSupported)
          check_ipo_supported(RESULT ipo_supported OUTPUT ipo_error)
          if(ipo_supported)
              set_property(TARGET webp4j PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
              message(STATUS "Link-time optimization (LTO) enabled")
          endif()
      endif()
  endif()

  # Strip symbols to reduce library file size (Release mode)
  if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
      if(UNIX AND NOT APPLE)
          target_link_options(webp4j PRIVATE -s)
      elseif(APPLE)
          target_link_options(webp4j PRIVATE -Wl,-dead_strip)
      endif()
  endif()

  # ============================================
  # Platform-Specific Configuration (Industry Standard Approach)
  # ============================================

  # Normalize architecture names (industry standard, matches mainstream projects like JNA, LWJGL)
  if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64|AMD64)$")
      set(ARCH_NAME "x64")  # Unified x64
  elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64|ARM64)$")
      if(APPLE)
          set(ARCH_NAME "arm64")  # macOS Apple Silicon uses arm64
      else()
          set(ARCH_NAME "aarch64")  # Linux ARM 64-bit uses aarch64
      endif()
  elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(arm|armv7l|armv6l|armv7|armv6)$")
      set(ARCH_NAME "arm")  # ARM 32-bit (Raspberry Pi, etc.)
  elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(i686|i386|x86)$")
      set(ARCH_NAME "x86")
  else()
      set(ARCH_NAME ${CMAKE_SYSTEM_PROCESSOR})
  endif()

  # Normalize platform names
  if(WIN32)
      set(PLATFORM_NAME "windows")
      set(LIB_EXTENSION "dll")
      set(LIB_PREFIX "")  # Windows doesn't use lib prefix
  elseif(APPLE)
      set(PLATFORM_NAME "mac")
      set(LIB_EXTENSION "dylib")
      set(LIB_PREFIX "lib")  # macOS uses lib prefix
  elseif(UNIX)
      set(PLATFORM_NAME "linux")
      set(LIB_EXTENSION "so")
      set(LIB_PREFIX "lib")  # Linux uses lib prefix
  else()
      message(FATAL_ERROR "Unsupported platform: ${CMAKE_SYSTEM_NAME}")
  endif()

  # Set output filename (naming convention)
  # Pattern: ${LIB_PREFIX}webp4j-${PLATFORM_NAME}-${ARCH_NAME}.${LIB_EXTENSION}
  # Examples:
  #   Windows x64   -> webp4j-windows-x64.dll
  #   Linux x64     -> libwebp4j-linux-x64.so
  #   Linux aarch64 -> libwebp4j-linux-aarch64.so
  #   Linux arm     -> libwebp4j-linux-arm.so
  #   macOS x64     -> libwebp4j-mac-x64.dylib
  #   macOS arm64   -> libwebp4j-mac-arm64.dylib
  set(LIB_SUFFIX "-${PLATFORM_NAME}-${ARCH_NAME}.${LIB_EXTENSION}")
  set_target_properties(webp4j PROPERTIES
      PREFIX "${LIB_PREFIX}"
      SUFFIX "${LIB_SUFFIX}"
      OUTPUT_NAME "webp4j"
  )

  # Debug information
  message(STATUS "Building for: ${PLATFORM_NAME}-${ARCH_NAME}")
  message(STATUS "Library name: ${LIB_PREFIX}webp4j${LIB_SUFFIX}")

  # ============================================
  # Installation Configuration
  # ============================================

  # Install to Maven resources directory
  set(INSTALL_DIR "${CMAKE_SOURCE_DIR}/src/main/resources/native")

  install(TARGETS webp4j
      LIBRARY DESTINATION ${INSTALL_DIR}
      RUNTIME DESTINATION ${INSTALL_DIR}
  )
