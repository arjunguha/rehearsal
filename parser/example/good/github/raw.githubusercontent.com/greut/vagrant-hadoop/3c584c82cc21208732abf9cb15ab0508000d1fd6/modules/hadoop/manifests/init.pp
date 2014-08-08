# -*- mode: ruby -*-
# vi: set ft=ruby nowrap sw=2 sts=2 ts=8 noet:

class hadoop {
  $hadoop_home = "/opt/hadoop"
  $hadoop_ver = "0.21.0"
  $pig_home = "/opt/pig"
  $pig_ver = "0.9.2"

  exec { "download_hadoop":
    command => "wget -O /tmp/hadoop.tgz http://archive.apache.org/dist/hadoop/core/hadoop-${hadoop_ver}/hadoop-${hadoop_ver}.tar.gz",
    path => $path,
    unless => "ls /opt | grep hadoop-${hadoop_ver}",
    require => Package["openjdk-7-jdk"]
  }

  exec { "download_pig":
    command => "wget -O /tmp/pig.tgz http://mirror.switch.ch/mirror/apache/dist/pig/pig-${pig_ver}/pig-${pig_ver}.tar.gz",
    path => $path,
    unless => "ls /opt | grep pig-${pig_ver}",
    require => Exec["unpack_hadoop"]
  }

  exec { "unpack_hadoop":
    command => "tar -xzf /tmp/hadoop.tgz -C /opt",
    path => $path,
    creates => "${hadoop_home}-${hadoop_ver}",
    require => Exec["download_hadoop"]
  }

  exec { "unpack_pig":
    command => "tar -xzf /tmp/pig.tgz -C /opt",
    path => $path,
    creates => "${pig_home}-${pig_ver}",
    require => Exec["download_pig"]
  }

  exec { "symlink_hadoop":
    command => "mkdir -p /opt/hadoop_install; ln -s ${hadoop_home}-${hadoop_ver} /opt/hadoop_install/hadoop",
    path => $path,
    creates => "/opt/hadoop_install/hadoop",
    require => Exec["unpack_hadoop"]
  }
  
  exec { "symlink_pig":
    command => "mkdir -p /opt/pig_install; ln -s ${pig_home}-${pig_ver} /opt/pig_install/pig",
    path => $path,
    creates => "/opt/pig_install/pig",
    require => Exec["unpack_pig"]
  }

  exec { "logs":
    command => "mkdir -p ${hadoop_home}-${hadoop_ver}/logs; chmod 0777 ${hadoop_home}-${hadoop_ver}/logs",
    path => $path,
    creates => "/opt/hadoop_install/hadoop/logs",
    require => Exec["unpack_hadoop"]
  }

  exec { "tmp":
    command => "mkdir -p ${hadoop_home}-${hadoop_ver}/tmp; chmod 0777 ${hadoop_home}-${hadoop_ver}/tmp",
    path => $path,
    creates => "/opt/hadoop_install/hadoop/tmp",
    require => Exec["unpack_hadoop"]
  }

  file { "${hadoop_home}-${hadoop_ver}/conf/hadoop-env.sh":
    source => "puppet:///files/modules/hadoop/conf/hadoop-env.sh",
    mode => 644,
    owner => root,
    group => root,
    require => Exec["unpack_hadoop"]
  }

  file { "${hadoop_home}-${hadoop_ver}/conf/core-site.xml":
    source => "puppet:///files/modules/hadoop/conf/core-site.xml",
    mode => 644,
    owner => root,
    group => root,
    require => File["${hadoop_home}-${hadoop_ver}/conf/hadoop-env.sh"]
  }

  file { "${hadoop_home}-${hadoop_ver}/conf/hdfs-site.xml":
    source => "puppet:///files/modules/hadoop/conf/hdfs-site.xml",
    mode => 644,
    owner => root,
    group => root,
    require => File["${hadoop_home}-${hadoop_ver}/conf/core-site.xml"]
  }

  file { "${hadoop_home}-${hadoop_ver}/conf/mapred-site.xml":
    source => "puppet:///files/modules/hadoop/conf/mapred-site.xml",
    mode => 644,
    owner => root,
    group => root,
    require => File["${hadoop_home}-${hadoop_ver}/conf/hdfs-site.xml"]
  }

  exec { "format":
    command => "${hadoop_home}-${hadoop_ver}/bin/hadoop namenode -format",
    path => $path,
    require => File["${hadoop_home}-${hadoop_ver}/conf/mapred-site.xml"]
  }
}
