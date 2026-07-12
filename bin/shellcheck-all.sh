#!/bin/bash
## sc2034 bemängelt unused variables - das passt nicht zu dem
## generische include der config.txt
shellcheck -a -x -e SC2034 --color=never --format=gcc bin/*.sh
