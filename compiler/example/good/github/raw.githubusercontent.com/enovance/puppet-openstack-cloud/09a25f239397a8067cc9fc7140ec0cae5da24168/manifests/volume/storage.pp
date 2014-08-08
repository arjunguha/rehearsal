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
# Volume storage
#
# === Parameters
#
# [*ks_keystone_internal_proto*]
#   (optional) Protocol used to connect to API. Could be 'http' or 'https'.
#   Defaults to 'http'
#
# [*ks_keystone_internal_host*]
#   (optional) Internal Hostname or IP to connect to Keystone API
#   Defaults to '127.0.0.1'
#
# [*ks_keystone_internal_port*]
#   (optional) TCP port to connect to Keystone API from admin network
#   Default to '5000'
#
# [*ks_cinder_password*]
#   (optional) Password used by Cinder to connect to Keystone API
#   Defaults to 'secrete'
#
# [*cinder_backends*]
#   (optionnal) Hash of the Cinder backends to enable
#   Example:
#   cinder_backends = {
#     'rbd' => {
#        'lowcost'  => { 'rbd_pool' => 'slow', 'rbd_user' => 'cinder', 'rbd_secret_uuid' => '123' },
#        'standard' => { 'rbd_pool' => 'normal', 'rbd_user' => 'cinder', 'rbd_secret_uuid' => '123' }
#     },
#     'netapp' => {
#        'premium' => { 'netapp_server_hostname' => 'netapp.host', 'netapp_login' => 'joe', 'netapp_password' => 'secret' }
#     }
#   }
#   Defaults to undef to maintain backward compatibility.
#


class cloud::volume::storage(
  $cinder_backends                         = undef,
  $ks_keystone_internal_proto              = 'http',
  $ks_keystone_internal_port               = '5000',
  $ks_keystone_internal_host               = '127.0.0.1',
  $ks_cinder_password                      = 'secrete',
  $cinder_rbd_pool                         = 'volumes',
  $cinder_rbd_user                         = 'cinder',
  $cinder_rbd_secret_uuid                  = undef,
  $cinder_rbd_conf                         = '/etc/ceph/ceph.conf',
  $cinder_rbd_flatten_volume_from_snapshot = false,
  $cinder_rbd_max_clone_depth              = '5',
  # Deprecated parameters
  $glance_api_version                      = '2',
) {

  include 'cloud::volume'

  include 'cinder::volume'

  if $cinder_backends {
    $rbd_backends = has_key($cinder_backends, 'rbd') ? {
      false   => merge({}, {}),
      default => $cinder_backends['rbd']
    }

    if has_key($cinder_backends, 'netapp') {
      $netapp_backends = $cinder_backends['netapp']
      create_resources('cloud::volume::backend::netapp', $netapp_backends)
    }
    else {
      $netapp_backends = { }
    }

    class { 'cinder::backends':
      enabled_backends => keys(merge($rbd_backends, $netapp_backends))
    }

    # Manage Volume types.
    # It allows to the end-user to choose from which backend he would like to provision a volume.
    # Cinder::Type requires keystone credentials
    Cinder::Type <| |> {
      os_tenant_name => 'services',
      os_username    => 'cinder',
      os_password    => $ks_cinder_password,
      os_auth_url    => "${ks_keystone_internal_proto}://${ks_keystone_internal_host}:${ks_keystone_internal_port}/v2.0"
    }
  }
  # For backward compatibility when not using multi-backend
  else {
    $rbd_backends = { 'DEFAULT' => { } }
  }

  if ! empty($rbd_backends) {
    create_resources('cloud::volume::backend::rbd', $rbd_backends,
      { rbd_pool                         => $cinder_rbd_pool,
        rbd_user                         => $cinder_rbd_user,
        rbd_secret_uuid                  => $cinder_rbd_secret_uuid,
        rbd_ceph_conf                    => $cinder_rbd_conf,
        rbd_flatten_volume_from_snapshot => $cinder_rbd_flatten_volume_from_snapshot,
        rbd_max_clone_depth              => $cinder_rbd_max_clone_depth })
  }
}
