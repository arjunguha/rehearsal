class site_webapp::haproxy {

  include site_haproxy

  $haproxy     = hiera('haproxy')

  # Template uses $global_options, $defaults_options
  concat::fragment { 'leap_haproxy_webapp_couchdb':
    target  => '/etc/haproxy/haproxy.cfg',
    order   => '20',
    content => template('site_webapp/haproxy_couchdb.cfg.erb'),
  }
}
