# Puppet class for riak

class riak {
  exec { "install_riak":
    command => "/vagrant/files/scripts/install_riak",
    path => "/bin:/usr/bin",
  }

  exec { "install_riaknostic":
    command => "/vagrant/files/scripts/install_riaknostic",
    path => "/bin:/usr/bin",
  }

  package { "riak":
    ensure => present,
    require => [ Exec["install_riak"], Exec["install_riaknostic"] ],
  }

  file { "app.config":
    path => "/etc/riak/app.config",
    ensure => file,
    owner => "riak",
    group => "riak",
    mode => "0644",
    content => template("riak/app.config.erb"),
    require => Package["riak"],
  }

  file { "vm.args":
    path => "/etc/riak/vm.args",
    ensure => file,
    owner => "riak",
    group => "riak",
    mode => "0644",
    content => template("riak/vm.args.erb"),
    require => Package["riak"],
  }

  file { "riak-nodes":
    path => "/usr/bin/riak-nodes",
    ensure => file,
    owner => "root",
    group => "root",
    mode => "0755",
    content => template("riak/riak-nodes.erb"),
    require => Package["riak"],
  }

  exec { "flush_iptables":
    command => "iptables -F",
    path => "/sbin",
  }

  service { "riak":
    ensure => running,
    require => [
      Exec["flush_iptables"], Package["riak"],
      File["app.config"], File["vm.args"]
    ],
  }

  if $ip_addr != $join_ip {
    exec { "join_riak":
      command => "/vagrant/files/scripts/join_riak '${join_ip}'",
      path => "/bin:/usr/bin",
      require => Service["riak"],
    }
  }

  exec { "alias_riak":
    command => "/vagrant/files/scripts/alias_riak",
    path => "/usr/bin",
    require => Service["riak"],
  }
}

include riak
