class custom::config {
  
  # local variables
  $repository_path = "$custom::params::repository_path"
  $app_name = "$custom::params::app_name"
  $username = "$custom::params::username"
  $env_path = "$opdemand::inputs::env_path"
  
  # rebuild upstart conf files
  exec {"rebuild-upstart":
    path => ["/sbin", "/bin", "/usr/bin", "/usr/local/bin"],
    cwd => $repository_path,
    command => "foreman export upstart /etc/init -a $app_name -u $username -e $env_path -t /var/cache/opdemand",
    # rebuild on change of inputs.sh or the vcsrepo
    subscribe => [ File[$env_path], Vcsrepo[$repository_path] ],
    # notify the service on change
    notify => Service[$app_name],
    require => Class[Custom::Install],
  }
  
  
}