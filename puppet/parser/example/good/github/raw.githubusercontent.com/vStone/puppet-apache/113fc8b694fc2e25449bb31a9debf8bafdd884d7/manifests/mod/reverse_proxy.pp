# == Class: apache::mod::reverse_proxy
#
# This class allows you to set default options to append to a proxypass line.
#
# === Todo:
#
# TODO: Update documentation
#
class apache::mod::reverse_proxy (
  $default_proxy_pass_options = {},
  $notify_service             = undef,
  $ensure                     = 'present',
) {

  apache::config::loadmodule{'proxy':
    ensure         => $ensure,
    notify_service => $notify_service,
  }

}
