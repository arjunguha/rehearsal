# Sets up configuration common to all quantum nodes.
# Flags install individual services as needed
# This follows the suggest deployment from the Quantum Administrator Guide.
class grizzly::profile::quantum::common {
  $api_device = hiera('grizzly::network::api::device')
  $management_device = hiera('grizzly::network::management::device')
  $data_device = hiera('grizzly::network::data::device')
  $external_device = hiera('grizzly::network::external::device')

  $api_address = getvar("ipaddress_${api_device}")
  $management_address = getvar("ipaddress_${management_device}")
  $data_address = getvar("ipaddress_${data_device}")
  $external_address = getvar("ipaddress_${external_device}")

  $controller_management_address = hiera('grizzly::controller::address::management')
  $controller_api_address = hiera('grizzly::controller::address::api')

  $sql_password = hiera('grizzly::quantum::sql::password')
  $sql_connection =
    "mysql://quantum:${sql_password}@${controller_management_address}/quantum"

  class { '::quantum':
    rabbit_host        => $controller_management_address,
    rabbit_user        => hiera('grizzly::rabbitmq::user'),
    rabbit_password    => hiera('grizzly::rabbitmq::password'),
    debug              => hiera('grizzly::quantum::debug'),
    verbose            => hiera('grizzly::quantum::verbose'),
  }

  # everone gets an ovs agent (TODO true?)
  class { '::quantum::agents::ovs':
    enable_tunneling => 'True',
    local_ip         => $data_address,
    enabled          => true,
  }

  # everyone gets an ovs plugin (TODO true?)
  class  { '::quantum::plugins::ovs':
    sql_connection      => $sql_connection,
    tenant_network_type => 'gre',
  }
}
