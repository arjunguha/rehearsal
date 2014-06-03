class percona_xtradb_cluster::percona::configure {

  $thread_concurrency = $::processorcount * 2

  file { '/etc/my.cnf':
    ensure  => present,
    content => template("percona_xtradb_cluster/percona/my.cnf.erb"),
    require => Package['percona-xtradb-cluster-server-5.5'];
  }
}