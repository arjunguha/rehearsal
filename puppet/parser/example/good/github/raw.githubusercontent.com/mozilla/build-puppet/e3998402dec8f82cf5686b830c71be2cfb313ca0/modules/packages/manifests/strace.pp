# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
class packages::strace {
    case $::operatingsystem {
        CentOS, Ubuntu: {
            package {
                "strace":
                    ensure => latest;
            }
        }
        Darwin: {
            #dtrace is installed with base install image
        }
        default: {
            fail("cannot install on $::operatingsystem")
        }
    }
}
