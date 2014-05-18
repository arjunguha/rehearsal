# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
class dirs::opt::bmm {
    include dirs::opt
    file {
        "/opt/bmm":
            ensure => directory,
            mode => 0755;
    }
}
