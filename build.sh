#!/bin/bash

# Searchable JPA Build Script
# Author: SimpleCORE
# Description: Interactive build options selection script

set -e  # Exit script on error

# Color definitions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Logo output
echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║                        Searchable JPA                         ║"
echo "║                     Build Management Tool                     ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Current time output
echo -e "${BLUE}Build start time: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo ""

# Function: Get user input
get_user_choice() {
    local prompt="$1"
    local default="$2"
    
    echo -e "${YELLOW}${prompt}${NC}" >&2
    if [ -n "$default" ]; then
        echo -e "${CYAN}(Default: ${default})${NC}" >&2
    fi
    read -p "> " USER_INPUT
    
    if [ -z "$USER_INPUT" ] && [ -n "$default" ]; then
        USER_INPUT="$default"
    fi
}

# Function: Yes/No question
ask_yes_no() {
    local prompt="$1"
    local default="$2"
    
    while true; do
        get_user_choice "$prompt [y/n]" "$default"
        case "$USER_INPUT" in
            [Yy]|[Yy][Ee][Ss]|y|Y)
                return 0
                ;;
            [Nn]|[Nn][Oo]|n|N)
                return 1
                ;;
            *)
                echo -e "${RED}Please enter a valid value (y/n)${NC}"
                ;;
        esac
    done
}

# Function: Confirm GitHub Packages publish with typed confirmation
confirm_github_publish() {
    echo ""
    echo -e "${RED}⚠️  WARNING: GitHub Packages Publication ⚠️${NC}"
    echo -e "${YELLOW}You are about to publish to GitHub Packages.${NC}"
    echo -e "${YELLOW}This action will make your artifacts publicly available.${NC}"
    echo ""
    echo -e "${CYAN}Repository: https://github.com/simplecore-inc/searchable-jpa/packages${NC}"
    echo -e "${CYAN}Artifacts will be published with version: $(grep 'version =' build.gradle | head -1 | cut -d"'" -f2)${NC}"
    echo ""
    
    local confirmation
    while true; do
        echo -e "${YELLOW}To confirm, please type 'PUBLISH' (case sensitive):${NC}"
        read -p "> " confirmation
        
        if [ "$confirmation" = "PUBLISH" ]; then
            echo -e "${GREEN}Confirmation received. Proceeding with GitHub Packages publication...${NC}"
            return 0
        else
            echo -e "${RED}Confirmation failed. You typed: '$confirmation'${NC}"
            echo -e "${YELLOW}Please type exactly 'PUBLISH' to confirm, or press Ctrl+C to cancel.${NC}"
            echo ""
        fi
    done
}

# Function: Select build type
select_build_type() {
    local refresh_deps_status="disabled"
    REFRESH_DEPS="false"
    
    while true; do
        echo -e "${PURPLE}=== Build Type Selection ===${NC}"
        echo -e "${CYAN}Note: All builds will include 'clean' for consistency${NC}"
        echo ""
        
        # Build options box
        echo -e "  ${CYAN}┌──────────────────────────────────────────────┐${NC}"
        echo -e "  ${CYAN}│${NC}  ${YELLOW}1)${NC} Standard Build (exclude tests)           ${CYAN}│${NC}"
        echo -e "  ${CYAN}│${NC}  ${YELLOW}2)${NC} Full Build (include tests)               ${CYAN}│${NC}"
        echo -e "  ${CYAN}│${NC}  ${YELLOW}3)${NC} Publish to Local Maven Repository        ${CYAN}│${NC}"
        echo -e "  ${CYAN}│${NC}  ${YELLOW}4)${NC} Publish to GitHub Packages               ${CYAN}│${NC}"
        echo -e "  ${CYAN}└──────────────────────────────────────────────┘${NC}"
        echo ""
        
        # Show current refresh dependencies status
        if [ "$REFRESH_DEPS" = "true" ]; then
            refresh_deps_status="${GREEN}✓ ON${NC}"
        else
            refresh_deps_status="${RED}✗ OFF${NC}"
        fi
        echo -e "${CYAN}Refresh Dependencies: ${refresh_deps_status} ${YELLOW}(press 'r' to toggle)${NC}"
        echo ""
        
        get_user_choice "Select build type (1-4) or 'r' to toggle refresh:" "1"
        case "$USER_INPUT" in
            1|2|3|4)
                BUILD_TYPE="$USER_INPUT"
                return
                ;;
            [Rr]|r)
                if [ "$REFRESH_DEPS" = "true" ]; then
                    REFRESH_DEPS="false"
                    echo -e "${YELLOW}Refresh dependencies disabled${NC}"
                else
                    REFRESH_DEPS="true"
                    echo -e "${YELLOW}Refresh dependencies enabled${NC}"
                fi
                echo ""
                ;;
            *)
                echo -e "${RED}Please enter a number between 1-4 or 'r' to toggle refresh${NC}"
                echo ""
                ;;
        esac
    done
}



# Function: Execute build
execute_build() {
    local build_type="$1"
    local refresh_deps="$2"
    local gradle_cmd="./gradlew"
    local build_args="clean"  # Always start with clean
    
    # GitHub Packages confirmation
    if [ "$build_type" = "4" ]; then
        if ! confirm_github_publish; then
            echo -e "${YELLOW}GitHub Packages publication cancelled.${NC}"
            exit 0
        fi
    fi
    
    # Dependency refresh option
    if [ "$refresh_deps" = "true" ]; then
        build_args="$build_args --refresh-dependencies"
        echo -e "${YELLOW}Refreshing dependencies...${NC}"
    fi
    
    # Set command based on build type
    case "$build_type" in
        1)
            build_args="$build_args build -x test"
            echo -e "${GREEN}Starting clean + standard build (excluding tests)...${NC}"
            ;;
        2)
            build_args="$build_args build"
            echo -e "${GREEN}Starting clean + full build (including tests)...${NC}"
            ;;
        3)
            build_args="$build_args publishToMavenLocal -x test"
            echo -e "${GREEN}Starting clean + publishing to local Maven repository...${NC}"
            ;;
        4)
            build_args="$build_args publish -x test"
            echo -e "${GREEN}Starting clean + publishing to GitHub Packages...${NC}"
            ;;
    esac
    
    echo ""
    echo -e "${CYAN}Executing command: ${gradle_cmd} ${build_args}${NC}"
    echo ""
    
    # Record build start time
    start_time=$(date +%s)
    
    # Execute build
    if eval "${gradle_cmd} ${build_args}"; then
        # Calculate build completion time
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        minutes=$((duration / 60))
        seconds=$((duration % 60))
        
        echo ""
        echo -e "${GREEN}✅ Build completed successfully!${NC}"
        echo -e "${BLUE}Build time: ${minutes}m ${seconds}s${NC}"
        
        # Show build results
        show_build_results "$build_type"
        
    else
        echo ""
        echo -e "${RED}❌ Build failed.${NC}"
        echo -e "${YELLOW}Please check the logs to resolve the error.${NC}"
        exit 1
    fi
}

# Function: Show build results
show_build_results() {
    local build_type="$1"
    
    echo ""
    echo -e "${PURPLE}=== Build Results ===${NC}"
    
    case "$build_type" in
        1|2)
            echo -e "${CYAN}Generated JAR files:${NC}"
            find . -name "*.jar" -path "*/build/libs/*" | while read jar; do
                echo "  📦 $jar"
            done
            ;;
        3)
            echo -e "${CYAN}Local Maven repository location:${NC}"
            echo "  📁 ~/.m2/repository/dev/simplecore/searchable/"
            echo ""
            echo -e "${CYAN}Published artifacts:${NC}"
            find ~/.m2/repository/dev/simplecore/searchable/ -name "*.jar" -type f 2>/dev/null | head -10 | while read jar; do
                echo "  📦 $(basename $jar)"
            done
            ;;
        4)
            echo -e "${CYAN}Published to GitHub Packages${NC}"
            echo "  🌐 https://github.com/simplecore-inc/searchable-jpa/packages"
            ;;
    esac
}

# Function: Show system information
show_system_info() {
    echo ""
    echo -e "${PURPLE}=== System Information ===${NC}"
    echo -e "${CYAN}Java Version:${NC} $(java -version 2>&1 | head -n 1)"
    echo -e "${CYAN}Gradle Version:${NC} $(./gradlew --version | grep "Gradle" | head -n 1)"
    echo -e "${CYAN}Operating System:${NC} $(uname -s) $(uname -r)"
    echo -e "${CYAN}Processor:${NC} $(uname -m)"
    echo ""
}

# Main execution
main() {
    # Show system information
    show_system_info
    
    # Select build type (also handles refresh dependencies toggle)
    select_build_type
    build_type="$BUILD_TYPE"
    refresh_deps="$REFRESH_DEPS"
    
    # Confirmation message
    echo ""
    echo -e "${PURPLE}=== Build Configuration Confirmation ===${NC}"
    case "$build_type" in
        1) echo -e "${CYAN}Build Type:${NC} Clean + Standard Build (exclude tests)" ;;
        2) echo -e "${CYAN}Build Type:${NC} Clean + Full Build (include tests)" ;;
        3) echo -e "${CYAN}Build Type:${NC} Clean + Publish to Local Maven Repository" ;;
        4) echo -e "${CYAN}Build Type:${NC} Clean + Publish to GitHub Packages" ;;
    esac
    echo -e "${CYAN}Refresh Dependencies:${NC} $refresh_deps"
    echo ""
    
    if ask_yes_no "Do you want to proceed with the above configuration?" "y"; then
        execute_build "$build_type" "$refresh_deps"
    else
        echo -e "${YELLOW}Build cancelled.${NC}"
        exit 0
    fi
    
    echo ""
    echo -e "${GREEN}🎉 All tasks completed successfully!${NC}"
    echo -e "${BLUE}Completion time: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
}

# Execute script
main "$@" 