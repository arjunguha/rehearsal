# The profile to install the Glance API and Registry services
# Note that for this configuration API controls the storage,
# so it is on the storage node instead of the control node
class grizzly::profile::glance::api {
  $api_device = hiera('grizzly::network::api::device')
  $api_address = getvar("ipaddress_${api_device}")

  $management_device = hiera('grizzly::network::management::device')
  $management_address = getvar("ipaddress_${management_device}")

  $explicit_management_address = hiera('grizzly::storage::address::management')
  $explicit_api_address = hiera('grizzly::storage::address::api')

  $controller_address = hiera('grizzly::controller::address::management')

  if $management_address != $explicit_management_address {
    fail("Glance Auth setup failed. The inferred location of Glance from
    the grizzly::network::management::device hiera value i
    ${management_address}. The explicit address from
    grizzly::storage::address::management is ${explicit_management_address}.
    Please correct this difference.")
  }

  if $api_address != $explicit_api_address {
    fail("Glance Auth setup failed. The inferred location of Glance from
    the grizzly::network::management::device hiera value is
    ${api_address}. The explicit address from
    grizzly::storage::address::api is ${explicit_api_address}.
    Please correct this difference.")
  }

  # public API access
  firewall { '09292 - Glance API':
    proto  => 'tcp',
    state  => ['NEW'],
    action => 'accept',
    port   => '9292',
  }

  # public API access
  firewall { '09191 - Glance Registry':
    proto  => 'tcp',
    state  => ['NEW'],
    action => 'accept',
    port   => '9191',
  }

  $sql_password = hiera('grizzly::glance::sql::password')
  $sql_connection =
    "mysql://glance:${sql_password}@${controller_address}/glance"

  # database setup

  class { '::glance::api':
    keystone_password => hiera('grizzly::glance::password'),
    auth_host         => $controller_address,
    keystone_tenant   => 'services',
    keystone_user     => 'glance',
    sql_connection    => $sql_connection,
    registry_host     => hiera('grizzly::storage::address::management'),
    verbose           => hiera('grizzly::glance::verbose'),
    debug             => hiera('grizzly::glance::debug'),
  }

  class { '::glance::backend::file': }

  class { '::glance::registry':
    keystone_password => hiera('grizzly::glance::password'),
    sql_connection    => $sql_connection,
    auth_host         => $controller_address,
    keystone_tenant   => 'services',
    keystone_user     => 'glance',
    verbose           => true,
    debug             => true,
  }
}
