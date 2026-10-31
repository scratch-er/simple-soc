/* Note: this file is AI generated
 * Simplified SoC simulation on NVBoard.
 * A single loop drives the clock; no difftest, no debug command line.
 *
 * Optional argument: a binary image loaded into ROM at 0x30000000.
 */
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>

#include <nvboard.h>
#include <svdpi.h>
#include <verilated.h>
#include <verilated_vcd_c.h>

#include "VSimTop.h"

// Memory map (must match simple-soc address space)
static constexpr uint32_t ROM_BASE = 0x30000000;
static constexpr uint32_t ROM_SIZE = 0x00010000; // 64 KiB
static constexpr uint32_t RAM_BASE = 0x80000000;
static constexpr uint32_t RAM_SIZE = 0x00010000; // 64 KiB

// ROM and RAM content
static std::unique_ptr<uint32_t[]> rom;
static std::unique_ptr<uint32_t[]> ram;

static void load_image(const char *path) {
  std::ifstream ifs(path, std::ios::binary);
  if (!ifs) {
    std::cerr << "cannot open image '" << path << "'\n";
    std::exit(1);
  }
  // load into ROM
  ifs.read(reinterpret_cast<char *>(rom.get()), ROM_SIZE);
  size_t n = ifs.gcount() / sizeof(uint32_t);
  std::cout << "loaded " << n << " words from " << path << " into ROM at 0x"
            << std::hex << ROM_BASE << std::dec << "\n";
}

static uint32_t mem_read(uint32_t addr) {
  if (addr >= ROM_BASE && addr < ROM_BASE + ROM_SIZE) {
    return rom[(addr - ROM_BASE) / 4];
  }
  if (addr >= RAM_BASE && addr < RAM_BASE + RAM_SIZE) {
    return ram[(addr - RAM_BASE) / 4];
  }
  std::cerr << "memory read fault at addr=0x" << std::hex << addr << std::dec
            << "\n";
  return 0;
}

static void mem_write(uint32_t addr, uint32_t data) {
  if (addr >= RAM_BASE && addr < RAM_BASE + RAM_SIZE) {
    ram[(addr - RAM_BASE) / 4] = data;
    return;
  }
  std::cerr << "memory write fault at addr=0x" << std::hex << addr << std::dec
            << "\n";
}

extern "C" void dpi_c_mem_read(const svBitVecVal *addr, svBitVecVal *rdata) {
  *rdata = mem_read(*addr);
}

extern "C" void dpi_c_mem_write(const svBitVecVal *addr, const svBitVecVal *wdata) {
  mem_write(*addr, *wdata);
}

int main(int argc, char **argv) {
  rom = std::make_unique<uint32_t[]>(ROM_SIZE / sizeof(uint32_t));
  ram = std::make_unique<uint32_t[]>(RAM_SIZE / sizeof(uint32_t));

  if (argc >= 2) {
    load_image(argv[1]);
  } else {
    rom[0] = 0x00100073; // ebreak
  }

  auto contextp = std::make_unique<VerilatedContext>();
  auto dut = std::make_unique<VSimTop>(contextp.get());

  // bind NVBoard pins
  nvboard_bind_pin(&(dut->externalPins_uart0_tx), 1, UART_TX);
  nvboard_bind_pin(&(dut->externalPins_uart0_rx), 1, UART_RX);
  nvboard_bind_pin(&(dut->externalPins_mygpio_out), 16, LD15, LD14, LD13, LD12,
                   LD11, LD10, LD9, LD8, LD7, LD6, LD5, LD4, LD3, LD2, LD1, LD0);
  nvboard_bind_pin(&(dut->externalPins_mygpio_in), 16, SW15, SW14, SW13, SW12,
                   SW11, SW10, SW9, SW8, SW7, SW6, SW5, SW4, SW3, SW2, SW1, SW0);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_0), 8, SEG0A, SEG0B, SEG0C,
                   SEG0D, SEG0E, SEG0F, SEG0G, DEC0P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_1), 8, SEG1A, SEG1B, SEG1C,
                   SEG1D, SEG1E, SEG1F, SEG1G, DEC1P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_2), 8, SEG2A, SEG2B, SEG2C,
                   SEG2D, SEG2E, SEG2F, SEG2G, DEC2P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_3), 8, SEG3A, SEG3B, SEG3C,
                   SEG3D, SEG3E, SEG3F, SEG3G, DEC3P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_4), 8, SEG4A, SEG4B, SEG4C,
                   SEG4D, SEG4E, SEG4F, SEG4G, DEC4P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_5), 8, SEG5A, SEG5B, SEG5C,
                   SEG5D, SEG5E, SEG5F, SEG5G, DEC5P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_6), 8, SEG6A, SEG6B, SEG6C,
                   SEG6D, SEG6E, SEG6F, SEG6G, DEC6P);
  nvboard_bind_pin(&(dut->externalPins_mygpio_seg_7), 8, SEG7A, SEG7B, SEG7C,
                   SEG7D, SEG7E, SEG7F, SEG7G, DEC7P);
  nvboard_init();

  // reset
  dut->reset = true;
  for (int i = 0; i < 4; ++i) {
    dut->clock = !dut->clock;
    dut->eval();
  }
  dut->reset = false;

  // single simulation loop
  while (!contextp->gotFinish()) {
    dut->clock = !dut->clock;
    contextp->timeInc(1);
    dut->eval();
    if (dut->clock == 1) {
      nvboard_update();
    }
  }

  nvboard_quit();
  return 0;
}
