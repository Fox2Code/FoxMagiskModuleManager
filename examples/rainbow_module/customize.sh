#!/bin/sh

if [ ! "$ANSI_SUPPORT" == "true" ] || [ ! -n "$MMM_EXT_SUPPORT" ]; then
  abort "! This module need to be executed in Fox's Magisk Module Manager with ANSI support"
  exit 1
fi

ui_print "#!useExt"
mmm_exec() {
  ui_print "$(echo -e "#!$@")"
}

ESC=""
# ESC="\e"
RED="$ESC[91m"
ORANGE="$ESC[33m"
YELLOW="$ESC[93m"
GREEN="$ESC[92m"
CYAN="$ESC[96m"
BLUE="$ESC[94m"
PURPLE="$ESC[95m"
RESET="$ESC[0m"

ui_replace() {
  mmm_exec setLastLine "$1"
}

ui_print "${RED}R${ORANGE}A${YELLOW}I${GREEN}N${CYAN}B${BLUE}O${PURPLE}W${RESET}"
mmm_exec showLoading
sleep 1
ui_replace "${PURPLE}R${RED}A${ORANGE}I${YELLOW}N${GREEN}B${CYAN}O${BLUE}W${RESET}"
sleep 1
ui_replace "${BLUE}R${PURPLE}A${RED}I${ORANGE}N${YELLOW}B${GREEN}O${CYAN}W${RESET}"
sleep 1
ui_replace "${CYAN}R${BLUE}A${PURPLE}I${RED}N${ORANGE}B${YELLOW}O${GREEN}W${RESET}"
sleep 1
ui_replace "${GREEN}R${CYAN}A${BLUE}I${PURPLE}N${RED}B${ORANGE}O${YELLOW}W${RESET}"
sleep 1
ui_replace "${YELLOW}R${GREEN}A${CYAN}I${BLUE}N${PURPLE}B${RED}O${ORANGE}W${RESET}"
sleep 1
ui_replace "${ORANGE}R${YELLOW}A${GREEN}I${CYAN}N${BLUE}B${PURPLE}O${RED}W${RESET}"
sleep 1
ui_replace "${RED}R${ORANGE}A${YELLOW}I${GREEN}N${CYAN}B${BLUE}O${PURPLE}W${RESET}"
sleep 1
ui_replace "${PURPLE}R${RED}A${ORANGE}I${YELLOW}N${GREEN}B${CYAN}O${BLUE}W${RESET}"
sleep 1
ui_replace "${BLUE}R${PURPLE}A${RED}I${ORANGE}N${YELLOW}B${GREEN}O${CYAN}W${RESET}"
sleep 1
ui_replace "${CYAN}R${BLUE}A${PURPLE}I${RED}N${ORANGE}B${YELLOW}O${GREEN}W${RESET}"
sleep 1
ui_replace "${GREEN}R${CYAN}A${BLUE}I${PURPLE}N${RED}B${ORANGE}O${YELLOW}W${RESET}"
sleep 1
ui_replace "${YELLOW}R${GREEN}A${CYAN}I${BLUE}N${PURPLE}B${RED}O${ORANGE}W${RESET}"
sleep 1
ui_replace "${ORANGE}R${YELLOW}A${GREEN}I${CYAN}N${BLUE}B${PURPLE}O${RED}W${RESET}"
sleep 1
ui_replace "${RED}R${ORANGE}A${YELLOW}I${GREEN}N${CYAN}B${BLUE}O${PURPLE}W${RESET}"
mmm_exec hideLoading

abort "! Rainbow module is not installable :3"

