class site_config::vagrant {
  # class for vagrant nodes

  include site_shorewall::defaults
  # eth0 on vagrant nodes is the uplink if
  shorewall::interface { 'eth0':
    zone      => 'net',
    options   => 'tcpflags,blacklist,nosmurfs';
  }

}
