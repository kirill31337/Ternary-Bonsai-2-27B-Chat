// Exercise the pinned Prism logger; this is not a model/GPU performance test.
#include "log.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>

// Terminal detection and the never-used abort path are host boundaries.
bool tty_can_use_colors() { return false; }
extern "C" void ggml_abort(const char *, int, const char *, ...) { std::abort(); }

int main(int argc, char **argv) {
    for (int i=1; i+1<argc; ++i) {
        if (std::strcmp(argv[i], "--log-verbosity")==0)
            common_log_set_verbosity_thold(std::atoi(argv[i+1]));
    }
    for (int i=1; i<argc; ++i) {
        if (std::strcmp(argv[i], "--list-devices")==0) {
            std::puts("Vulkan0: fixture GPU (not physical hardware)");
            return 0;
        }
    }
    common_log_add(common_log_main(), GGML_LOG_LEVEL_INFO,
                   "srv load_model: n_ctx_slot = 16384\n");
    common_log_default_callback(GGML_LOG_LEVEL_INFO,
        "load_tensors: offloaded 8/65 layers to GPU\n", nullptr);
    common_log_default_callback(GGML_LOG_LEVEL_INFO,
        "load_tensors: Vulkan0 model buffer size = 500.25 MiB\n", nullptr);
    common_log_default_callback(GGML_LOG_LEVEL_INFO,
        "llama_context: Vulkan0 compute buffer size = 10.00 MiB\n", nullptr);
    common_log_flush(common_log_main());
}
