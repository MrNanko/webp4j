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

  # Set global compiler optimization flags for QEMU builds BEFORE adding dependencies
  # This ensures libwebp and giflib inherit the correct optimization level
  if(DEFINED ENV{WEBP4J_QEMU_BUILD} AND "$ENV{WEBP4J_QEMU_BUILD}" STREQUAL "1")
      # QEMU builds: Force -O1 to prevent GCC crashes in emulated environments
      # We need to:
      # 1. Set base flags without optimization
      # 2. Add -O1 to CMAKE_C_FLAGS and CMAKE_CXX_FLAGS to override subdirectory flags
      set(CMAKE_C_FLAGS_RELEASE "-DNDEBUG" CACHE STRING "Flags used by C compiler during release builds" FORCE)
      set(CMAKE_CXX_FLAGS_RELEASE "-DNDEBUG" CACHE STRING "Flags used by CXX compiler during release builds" FORCE)

      # Append -O1 to base flags - this will appear LAST in the command line
      string(APPEND CMAKE_C_FLAGS " -O1")
      string(APPEND CMAKE_CXX_FLAGS " -O1")

      message(STATUS "Global compiler optimization: -O1 (QEMU build environment)")
  else()
      # Native builds: Use maximum optimization
      set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "Flags used by C compiler during release builds" FORCE)
      set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "Flags used by CXX compiler during release builds" FORCE)
      message(STATUS "Global compiler optimization: -O3 (native build)")
  endif()

  # Find JNI
  find_package(JNI REQUIRED)
  include_directories(${JNI_INCLUDE_DIRS})

  # Always use bundled libwebp to ensure consistent behavior across platforms
  # This ensures libwebp is statically linked into our library
  message(STATUS "Using bundled libwebp from libwebp/ subdirectory")

      # Disable libwebp example tools build, only build libraries
      set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
      set(WEBP_BUILD_WEBPMUX ON CACHE BOOL "" FORCE)
      set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)

      # Conditionally disable CPU-specific SIMD optimizations for QEMU build environments
      # QEMU has known issues when compiling architecture-specific SIMD instructions:
      # - SSE/AVX (x86_64)
      # - NEON (ARM)
      # - MSA/MIPS32/MIPS DSP R2 (MIPS)
      # Set WEBP4J_QEMU_BUILD=1 when building under QEMU to avoid compilation segfaults
      if(DEFINED ENV{WEBP4J_QEMU_BUILD} AND "$ENV{WEBP4J_QEMU_BUILD}" STREQUAL "1")
          message(STATUS "SIMD optimizations: DISABLED (building under QEMU, avoiding SIMD compilation issues)")
          # Disable all SIMD variants to prevent QEMU segfaults during compilation
          set(WEBP_ENABLE_SIMD OFF CACHE BOOL "" FORCE)

          # Disable NEON (ARM SIMD) - affects ARM64 and ARM32 platforms
          set(WEBP_BUILD_NEON OFF CACHE BOOL "" FORCE)
          set(WEBP_ENABLE_SIMD_NEON OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_NEON OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_NEON_RTCD OFF CACHE BOOL "" FORCE)

          # Disable MSA (MIPS SIMD Architecture) - affects MIPS platforms
          set(WEBP_BUILD_MSA OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_MSA OFF CACHE BOOL "" FORCE)

          # Disable MIPS32 and MIPS DSP R2 optimizations - affects MIPS platforms
          set(WEBP_BUILD_MIPS32 OFF CACHE BOOL "" FORCE)
          set(WEBP_BUILD_MIPS_DSP_R2 OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_MIPS32 OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_MIPS_DSP_R2 OFF CACHE BOOL "" FORCE)

          # Disable SSE/AVX (x86_64 SIMD) - affects Intel/AMD platforms
          set(WEBP_BUILD_SSE2 OFF CACHE BOOL "" FORCE)
          set(WEBP_BUILD_SSE41 OFF CACHE BOOL "" FORCE)
          set(WEBP_BUILD_AVX2 OFF CACHE BOOL "" FORCE)
          # Additional runtime detection flags
          set(WEBP_ENABLE_SIMD_AVX2 OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_SSE2 OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_SSE41 OFF CACHE BOOL "" FORCE)
          set(WEBP_HAVE_AVX2 OFF CACHE BOOL "" FORCE)
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

  # ============================================
  # giflib Detection and Configuration
  # ============================================
  #
  # Strategy: Prioritize static linking to eliminate runtime dependencies
  #
  # Option: WEBP4J_USE_SYSTEM_GIFLIB (default: OFF)
  #   - OFF (default): Build giflib statically, no runtime dependencies
  #   - ON: Use system-installed giflib (requires libgif at runtime)
  #
  # Build modes:
  #   1. Static build (WEBP4J_USE_SYSTEM_GIFLIB=OFF):
  #      - Tier 1: Use bundled source in giflib/ directory (if exists)
  #      - Tier 2: Auto-download from SourceForge and build statically
  #      - Result: giflib is compiled into webp4j, no external dependencies
  #
  #   2. System build (WEBP4J_USE_SYSTEM_GIFLIB=ON):
  #      - Uses system giflib via find_library()
  #      - Requires: brew install giflib (macOS) or apt install libgif-dev (Linux)
  #      - Result: Dynamic linking, requires libgif at runtime
  #
  # ============================================

  # Configuration option (default: OFF for maximum portability)
  option(WEBP4J_USE_SYSTEM_GIFLIB "Use system-installed giflib instead of static build" OFF)

  if(WEBP4J_USE_SYSTEM_GIFLIB)
      # User explicitly requested system giflib
      message(STATUS "Searching for system-installed giflib...")
      find_library(GIFLIB_LIBRARY NAMES gif libgif)
      find_path(GIFLIB_INCLUDE_DIR gif_lib.h)

      if(GIFLIB_LIBRARY AND GIFLIB_INCLUDE_DIR)
          message(STATUS "✓ Using system giflib: ${GIFLIB_LIBRARY}")
          message(WARNING "System giflib requires runtime dependency: ${GIFLIB_LIBRARY}")
          set(GIFLIB_FOUND TRUE)
      else()
          message(FATAL_ERROR "WEBP4J_USE_SYSTEM_GIFLIB=ON but system giflib not found. Install via: brew install giflib (macOS) or apt install libgif-dev (Linux)")
      endif()
  else()
      # Default: Build giflib statically for maximum portability
      message(STATUS "Building giflib statically (no runtime dependencies)")

      # Tier 1: Check if giflib source is bundled in project
      if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/giflib/gif_lib.h")
          message(STATUS "✓ Using bundled giflib from giflib/ subdirectory")
          add_subdirectory(giflib EXCLUDE_FROM_ALL)
          set(GIFLIB_LIBRARY gif)
          set(GIFLIB_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/giflib")
          set(GIFLIB_FOUND TRUE)
          set(GIFLIB_STATIC TRUE)
      else()
          # Tier 2: Auto-download and build giflib from official source
          message(STATUS "Downloading giflib from official SourceForge repository...")

          include(FetchContent)

          # Download giflib 5.2.2 (June 2024): Latest stable release with security fixes
          # Official repo: https://sourceforge.net/projects/giflib/
          FetchContent_Declare(
              giflib
              GIT_REPOSITORY https://git.code.sf.net/p/giflib/code
              GIT_TAG        5.2.2
              GIT_SHALLOW    TRUE
          )

          FetchContent_GetProperties(giflib)
          if(NOT giflib_POPULATED)
              message(STATUS "Downloading giflib source code...")
              FetchContent_Populate(giflib)

              # Build giflib manually (giflib uses autotools, not CMake)
              # We create a minimal CMakeLists.txt to compile only what we need
              set(GIFLIB_SOURCE_DIR ${giflib_SOURCE_DIR})
              set(GIFLIB_BUILD_DIR ${CMAKE_BINARY_DIR}/giflib-build)
              file(MAKE_DIRECTORY ${GIFLIB_BUILD_DIR})

              # Create a minimal CMakeLists.txt for giflib
              # We only compile the core library files needed for GIF decoding
              file(WRITE ${GIFLIB_BUILD_DIR}/CMakeLists.txt "
cmake_minimum_required(VERSION 3.15)
project(giflib C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Build static library with core GIF decoding/encoding functions
add_library(gif STATIC
    ${GIFLIB_SOURCE_DIR}/dgif_lib.c      # GIF decoder
    ${GIFLIB_SOURCE_DIR}/egif_lib.c      # GIF encoder
    ${GIFLIB_SOURCE_DIR}/gifalloc.c      # Memory allocation
    ${GIFLIB_SOURCE_DIR}/gif_err.c       # Error handling
    ${GIFLIB_SOURCE_DIR}/gif_font.c      # Font support
    ${GIFLIB_SOURCE_DIR}/gif_hash.c      # Hash table
    ${GIFLIB_SOURCE_DIR}/openbsd-reallocarray.c  # Safe realloc
)

target_include_directories(gif PUBLIC ${GIFLIB_SOURCE_DIR})
")

              # Add giflib as subdirectory
              add_subdirectory(${GIFLIB_BUILD_DIR} ${CMAKE_BINARY_DIR}/giflib-build-bin EXCLUDE_FROM_ALL)

              set(GIFLIB_LIBRARY gif)
              set(GIFLIB_INCLUDE_DIR ${GIFLIB_SOURCE_DIR})
              set(GIFLIB_FOUND TRUE)
              set(GIFLIB_STATIC TRUE)
              message(STATUS "✓ giflib downloaded and configured for static linking")
          endif()
      endif()
  endif()

  # Add header file search paths
  include_directories(
      ${CMAKE_CURRENT_SOURCE_DIR}/src/main/c
      ${CMAKE_CURRENT_SOURCE_DIR}/libwebp/src
      ${CMAKE_CURRENT_BINARY_DIR}/libwebp/src
  )

  if(GIFLIB_FOUND)
      include_directories(${GIFLIB_INCLUDE_DIR})
  endif()

  # Source files
  set(SOURCES src/main/c/webp4j.c)

  # Add GIF decoder if giflib is available
  if(GIFLIB_FOUND)
      list(APPEND SOURCES src/main/c/gif_decoder.c)
      add_compile_definitions(HAVE_GIFLIB=1)
      message(STATUS "GIF decoder enabled")
  else()
      message(STATUS "GIF decoder disabled (giflib not available)")
  endif()

  # Create shared library
  add_library(webp4j SHARED ${SOURCES})

  # Link libraries
  # - webp (main library)
  # - sharpyuv (libwebp dependency)
  # - libwebpmux (required for animated WebP encoding)
  # - gif (optional, for native GIF decoding)
  target_link_libraries(webp4j PRIVATE
      ${JNI_LIBRARIES}
      webp
      sharpyuv
      libwebpmux
      webpdemux
  )

  # Link giflib if available
  if(GIFLIB_FOUND)
      target_link_libraries(webp4j PRIVATE ${GIFLIB_LIBRARY})
  endif()

  # On Linux, use --whole-archive to ensure all symbols are included
  if(UNIX AND NOT APPLE)
      # Add math library (corresponds to -lm in manual compilation)
      target_link_libraries(webp4j PRIVATE m)
  endif()

  # ============================================
  # Compiler Warning Options
  # ============================================

  if(MSVC)
      # MSVC compiler options
      target_compile_options(webp4j PRIVATE
          /W4                 # Warning level 4
          /WX-                # Warnings not as errors
      )
  else()
      # GCC/Clang compiler options
      target_compile_options(webp4j PRIVATE
          -Wall               # Enable all warnings
          -Wextra             # Enable extra warnings
          -pedantic           # Strict standard compliance
          $<$<CONFIG:Debug>:-g>    # Debug mode: debug info
          $<$<CONFIG:Debug>:-O0>   # Debug mode: no optimization
      )

      # Link-time optimization (LTO)
      # Disable LTO in QEMU build environments to avoid compiler crashes
      if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug" AND NOT (DEFINED ENV{WEBP4J_QEMU_BUILD} AND "$ENV{WEBP4J_QEMU_BUILD}" STREQUAL "1"))
          include(CheckIPOSupported)
          check_ipo_supported(RESULT ipo_supported OUTPUT ipo_error)
          if(ipo_supported)
              set_property(TARGET webp4j PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
              message(STATUS "Link-time optimization (LTO) enabled")
          endif()
      else()
          if(DEFINED ENV{WEBP4J_QEMU_BUILD} AND "$ENV{WEBP4J_QEMU_BUILD}" STREQUAL "1")
              message(STATUS "Link-time optimization (LTO) disabled (QEMU build environment)")
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
  #   Windows x64     -> webp4j-windows-x64.dll
  #   Windows aarch64 -> webp4j-windows-aarch64.dll
  #   Linux x64       -> libwebp4j-linux-x64.so
  #   Linux aarch64   -> libwebp4j-linux-aarch64.so
  #   Linux arm       -> libwebp4j-linux-arm.so
  #   macOS x64       -> libwebp4j-mac-x64.dylib
  #   macOS arm64     -> libwebp4j-mac-arm64.dylib
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

  # Create installation directory if it doesn't exist
  file(MAKE_DIRECTORY ${INSTALL_DIR})

  install(TARGETS webp4j
      LIBRARY DESTINATION ${INSTALL_DIR}
      RUNTIME DESTINATION ${INSTALL_DIR}
  )
