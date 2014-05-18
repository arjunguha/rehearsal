#
# Copyright (C) 2014 eNovance SAS <licensing@enovance.com>
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License. You may obtain
# a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
#
# == Class: cloud::compute::hypervisor
#
# Hypervisor Compute node
#
# === Parameters:
#
# [*has_ceph]
#   (optional) Enable or not ceph capabilities on compute node.
#   If Ceph is used as a backend for Cinder or Nova, this option should be
#   set to True.
#   Default to false.
#

class cloud::compute::hypervisor(
  $server_proxyclient_address = '127.0.0.1',
  $libvirt_type               = 'kvm',
  $ks_nova_public_proto       = 'http',
  $ks_nova_public_host        = '127.0.0.1',
  $nova_ssh_private_key       = undef,
  $nova_ssh_public_key        = undef,
  $spice_port                 = 6082,
  $cinder_rbd_user            = 'cinder',
  $nova_rbd_pool              = 'vms',
  $nova_rbd_secret_uuid       = undef,
  $has_ceph                   = false
) {

  include 'cloud::compute'
  include 'cloud::telemetry'
  include 'cloud::network'

  file{ '/var/lib/nova/.ssh':
    ensure  => directory,
    mode    => '0700',
    owner   => 'nova',
    group   => 'nova',
    require => Class['nova']
  } ->
  file{ '/var/lib/nova/.ssh/id_rsa':
    ensure  => present,
    mode    => '0600',
    owner   => 'nova',
    group   => 'nova',
    content => $nova_ssh_private_key
  } ->
  file{ '/var/lib/nova/.ssh/authorized_keys':
    ensure  => present,
    mode    => '0600',
    owner   => 'nova',
    group   => 'nova',
    content => $nova_ssh_public_key
  } ->
  file{ '/var/lib/nova/.ssh/config':
    ensure  => present,
    mode    => '0600',
    owner   => 'nova',
    group   => 'nova',
    content => "
Host *
    StrictHostKeyChecking no
"
  }

  class { 'nova::compute':
    enabled         => true,
    vnc_enabled     => false,
    #TODO(EmilienM) Bug #1259545 currently WIP:
    virtio_nic      => false,
    neutron_enabled => true
  }

  class { 'nova::compute::spice':
    server_listen              => '0.0.0.0',
    server_proxyclient_address => $server_proxyclient_address,
    proxy_host                 => $ks_nova_public_host,
    proxy_protocol             => $ks_nova_public_proto,
    proxy_port                 => $spice_port

  }

  if $::operatingsystem == 'RedHat' {
    file { '/etc/libvirt/qemu.conf':
      ensure => file,
      source => 'puppet:///modules/cloud/qemu/qemu.conf',
      owner  => root,
      group  => root,
      mode   => '0644',
      notify => Service['libvirtd']
    }
    # Nova support for RBD backend is not supported in Red Hat packages
    if $has_ceph {
      warning('Red Hat does not support RBD backend for VMs.')
    }
    $has_ceph_real = false
  } else {
    $has_ceph_real = $has_ceph
  }

  if $::operatingsystem == 'Ubuntu' {
    service { 'dbus':
      ensure => running,
      enable => true,
      before => Class['nova::compute::libvirt'],
    }
  }

  Service<| title == 'dbus' |> { enable => true }

  Service<| title == 'libvirt-bin' |> { enable => true }

  class { 'nova::compute::neutron': }

  if $has_ceph_real {

    $libvirt_disk_cachemodes_real = ['network=writeback']
    include 'cloud::storage::rbd'

    # TODO(EmilienM) Temporary, while https://review.openstack.org/#/c/72440 got merged
    nova_config {
      'DEFAULT/libvirt_images_type':          value => 'rbd';
      'DEFAULT/libvirt_images_rbd_pool':      value => $nova_rbd_pool;
      'DEFAULT/libvirt_images_rbd_ceph_conf': value => '/etc/ceph/ceph.conf';
      'DEFAULT/rbd_user':                     value => $cinder_rbd_user;
      'DEFAULT/rbd_secret_uuid':              value => $nova_rbd_secret_uuid;
    }

    File <<| tag == 'ceph_compute_secret_file' |>>
    Exec <<| tag == 'get_or_set_virsh_secret' |>>
    Exec <<| tag == 'set_secret_value_virsh' |>>

    # Configure Ceph keyring
    Ceph::Key <<| title == $cinder_rbd_user |>>

    # If Cinder & Nova reside on the same node, we need a group
    # where nova & cinder users have read permissions.
    ensure_resource('group', 'cephkeyring', {
      ensure => 'present'
    })

    ensure_resource ('exec','add-nova-to-group', {
      'command' => 'usermod -a -G cephkeyring nova',
      'path'    => ['/usr/sbin', '/usr/bin', '/bin', '/sbin'],
      'unless'  => 'groups nova | grep cephkeyring'
    })

    ensure_resource('file', "/etc/ceph/ceph.client.${cinder_rbd_user}.keyring", {
      owner   => 'root',
      group   => 'cephkeyring',
      mode    => '0440',
      require => Ceph::Key[$cinder_rbd_user],
    })

    Concat::Fragment <<| title == 'ceph-client-os' |>>
  } else {
    $libvirt_disk_cachemodes_real = []
  }

  class { 'nova::compute::libvirt':
    libvirt_type            => $libvirt_type,
    # Needed to support migration but we still use Spice:
    vncserver_listen        => '0.0.0.0',
    migration_support       => true,
    libvirt_disk_cachemodes => $libvirt_disk_cachemodes_real
  }

  # Extra config for nova-compute
  nova_config {
    'DEFAULT/libvirt_inject_key':        value => false;
    'DEFAULT/libvirt_inject_partition':  value => '-2';
    'DEFAULT/live_migration_flag':       value => 'VIR_MIGRATE_UNDEFINE_SOURCE,VIR_MIGRATE_PEER2PEER,VIR_MIGRATE_LIVE,VIR_MIGRATE_PERSIST_DEST';
  }

  class { 'ceilometer::agent::compute': }

}
