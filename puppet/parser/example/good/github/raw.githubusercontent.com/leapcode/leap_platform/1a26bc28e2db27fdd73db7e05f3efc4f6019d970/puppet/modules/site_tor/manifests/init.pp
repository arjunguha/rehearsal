class site_tor {
  tag 'leap_service'
  Class['site_config::default'] -> Class['site_tor']

  $tor            = hiera('tor')
  $bandwidth_rate = $tor['bandwidth_rate']
  $tor_type       = $tor['type']
  $nickname       = $tor['nickname']
  $contact_emails = join($tor['contacts'],', ')
  $family         = $tor['family']

  $address        = hiera('ip_address')

  class { 'tor::daemon': }
  tor::daemon::relay { $nickname:
    port             => 9001,
    address          => $address,
    contact_info     => obfuscate_email($contact_emails),
    bandwidth_rate   => $bandwidth_rate,
    my_family        => $family
  }

  if ( $tor_type == 'exit'){
    tor::daemon::directory { $::hostname: port => 80 }
  }
  else {
    tor::daemon::directory { $::hostname:
      port            => 80,
      port_front_page => '';
    }
    include site_tor::disable_exit
  }

  include site_shorewall::tor

}
