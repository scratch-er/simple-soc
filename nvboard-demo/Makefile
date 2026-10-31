# Note: this file is AI generated
# Directory layout (relative to this directory, which lives inside simple-soc)
SIMPLE_SOC_DIR := $(abspath ..)
NPC_DIR        := $(abspath npc)

# NVBoard can be anywhere: it must be provided via the environment
NVBOARD_HOME ?= $(error please set NVBOARD_HOME to the NVBoard directory)

BUILD_DIR := build
SIM_EXE   := $(BUILD_DIR)/VSimTop

all: $(SIM_EXE)

# NVBoard (provides NVBOARD_ARCHIVE, adds sdl2 flags)
-include $(NVBOARD_HOME)/scripts/nvboard.mk

CXXFLAGS += -std=c++17 -O3 -g -flto
CXXFLAGS += -I$(NVBOARD_HOME)/usr/include
LDFLAGS  += -O3 -g -flto

# Verilator flags
VFLAGS += --autoflush
VFLAGS += --timescale "1ns/1ns" --no-timing
VFLAGS += -CFLAGS "$(CXXFLAGS)" -LDFLAGS "$(LDFLAGS)"

# Elaborate the Chisel SimpleSoC (generates build/sim/*.sv under simple-soc)
$(SIMPLE_SOC_DIR)/build/sim/SimTop.sv:
	cd $(SIMPLE_SOC_DIR) && sbt "runMain simplesoc.Elaborate"

# Module search paths:
#  - simple-soc generated SV and its handwritten simulation models
#  - the NPC core directory (`NpcSocWrapper.sv` and the core implementation,
#    which is added to this directory separately)
VINCLUDE += -I$(SIMPLE_SOC_DIR)/build/sim
VINCLUDE += -I$(SIMPLE_SOC_DIR)/src/main/sv
VINCLUDE += -I$(NPC_DIR)

VSRCS := $(SIMPLE_SOC_DIR)/build/sim/SimTop.sv

$(SIM_EXE): $(VSRCS) main.cc $(NVBOARD_ARCHIVE)
	@mkdir -p $(dir $@)
	verilator --top SimTop $(VFLAGS) $(VINCLUDE) \
		--Mdir $(BUILD_DIR) --cc --build --exe \
		$(VSRCS) main.cc $(NVBOARD_ARCHIVE)

IMG ?=
sim: $(SIM_EXE)
	$(SIM_EXE) $(IMG)

clean:
	rm -rf $(BUILD_DIR)

.PHONY: all sim clean
