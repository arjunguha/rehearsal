# Class: quartermaster::windowsmedia
#
# This Class defines Windows Media to the pxe infrastructrure
# based on the name of the ISO provided
# ISOs can be take offical unmodified ISOs and it will parse the name
# determining infromation to generate unattend.xml for the media
#
# Parameters: none
#
# Actions:
#
# Sample Usage:
#    quartermaster::windowsmedia{"en_windows_8_enterprise_x86_dvd_917587.iso": activationkey => "XXXXX-XXXXX-XXXXX-XXXXX-XXXXX"}
#

define quartermaster::windowsmedia( $activationkey ) {
  $isofile  = $name
#    $iso_path = "${quartermaster::wwwroot}/WinPE/ISO/${name}"
  $iso_path = "${quartermaster::winpe::windows_isos}/${name}"

# Windows Server 2012
  if $name =~ /([a-z]+)_([a-zA-Z_\-]+)_([0-9]+)_([x0-9]+)_dvd_([0-9]+).iso/ {
    $w_lang          = $1
    $w_dist          = $2
    $w_release       = $3
    $w_media_arch    = $4
    $w_build         = $5
  }
# Windows Server 2012 R2
  if $name =~ /([a-z]+)_([a-zA-Z_\-]+)_([0-9]+)_(r2)_([x0-9]+)_dvd_([0-9]+).iso/ {
    $w_lang          = $1
    $w_dist          = $2
    $w_release       = "${3}${4}"
    $w_media_arch    = $5
    $w_build         = $6
  }

# Windows 8
#if $name =~ /([a-z]+)_([a-zA-Z]+)_([0-9]+)_([a-zA-Z]+)_([x0-9]+)_dvd_([0-9]+).iso/ {
  if $name =~ /([a-z]+)_([a-zA-Z]+)_([0-9]_[a-zA-Z]+)_([x0-9]+)_dvd_([0-9]+).iso/ {
    $w_lang          = $1
    $w_dist          = $2
    $w_release       = $3
    $w_media_arch    = $4
    $w_build         = $5
  }
# Windows 8.1
#if $name =~ /([a-z]+)_([a-zA-Z]+)_([0-9]+)_([a-zA-Z]+)_([x0-9]+)_dvd_([0-9]+).iso/ {
  if $name =~ /([a-z]+)_([a-zA-Z]+)_([0-9]_[0-9]_[a-zA-Z]+)_([x0-9]+)_dvd_([0-9]+).iso/ {
    $w_lang          = $1
    $w_dist          = $2
    $w_release       = $3
    $w_media_arch    = $4
    $w_build         = $5
  }

  $w_distro = $w_dist ?{
    /(microsoft_hyper-v_server)/    => 'hyper-v',
    /(windows_server)/              => 'server',
    default                         => $w_dist,
  }

  $w_arch = $w_media_arch ?{
    /(x64)/ => 'amd64',
    /(x86)/ => 'i386',
  }
  $w_flavor = "${w_distro}-${w_release}-${w_arch}"

#  $w_menu_option = $w_dist ?{
#    /(windows_server)/           =>'S',
#    /(microsoft_hyper-v_server)/ =>'V',
#    /(windows)/                  =>'W',
#  }
  $w_menu_option = $w_flavor ?{
    /(windows-8_enterprise-i386)/    =>'A',
    /(windows-8_enterprise-amd64)/   =>'B',
    /(windows-8_1_enterprise-i386)/  =>'C',
    /(windows-8_1_enterprise-amd64)/ =>'D',
    /(hyper-v-2012-amd64)/           =>'E',
    /(hyper-v-2012r2-amd64)/         =>'F',
    /(server-2012-amd64)/            =>'G',
    /(server-2012r2-amd64)/          =>'H',
    /(server-2012-i386)/             =>'I',
    /(server-2012r2-i386)/           =>'J',
  }
  $w_media_image_name = $w_flavor ?{
    /(windows-8_enterprise-i386)/       =>'Windows 8 ENTERPRISE',
    /(windows-8_enterprise-amd64)/      =>'Windows 8 ENTERPRISE',
    /(windows-8_professional-i386)/     =>'Windows 8 PROFESSIONAL',
    /(windows-8_professional-amd64)/    =>'Windows 8 PROFESSIONAL',
    /(windows-8_1_enterprise-i386)/     =>'Windows 8.1 ENTERPRISE',
    /(windows-8_1_enterprise-amd64)/    =>'Windows 8.1 ENTERPRISE',
    /(windows-8_1_professional-i386)/   =>'Windows 8.1 PROFESSIONAL',
    /(windows-8_1_professional-amd64)/  =>'Windows 8.1 PROFESSIONAL',
    /(hyper-v-2012-amd64)/              =>'Hyper-V Server 2012 SERVERHYPERCORE',
    /(hyper-v-2012r2-amd64)/            =>'Hyper-V Server 2012 R2 SERVERHYPERCORE',
    /(server-2012-i386)/                =>'Windows Server 2012 SERVERDATACENTER',
    /(server-2012-amd64)/               =>'Windows Server 2012 SERVERDATACENTER',
    /(server-2012r2-i386)/              =>'Windows Server 2012 R2 SERVERDATACENTER',
    /(server-2012r2-amd64)/             =>'Windows Server 2012 R2 SERVERDATACENTER',
# If you want to use Standard Licensing "Windows Server 2012 SERVERSTANDARD"
  }

#  $w_flavor = $w_dist ?{
#    /(windows_server)/           =>'server',
#    /(microsoft_hyper-v_server)/ =>'hyper-v',
#    /(windows)/                  =>'client',
#  }

  notify {"${name}: WINDOWS LANGUAGE: ${w_lang}": }
  notify {"${name}: WINDOWS DISTRIBUTION ${w_distro}": }
  notify {"${name}: WINDOWS RELEASE: ${w_release}": }
  notify {"${name}: WINDOWS MEDIA ARCH: ${w_media_arch}": }
  notify {"${name}: WINDOWS BUILD NUMBER: ${w_build}": }
  notify {"${name}: WINDOWS ARCH: ${w_arch}": }
  notify {"${name}: WINDOWS FLAVOR: ${w_flavor}":}
  notify {"${name}: WINDOWS MEDIA IMAGE NAME: ${w_media_image_name}":}

#  file {"w_iso_file_${name}":
#    path => "${iso_path}/${name}",
#  }


  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}":
      ensure  => directory,
      recurse => true,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      require =>  File[ "${quartermaster::wwwroot}/microsoft" ],
    }
  }
  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}":
      ensure  => directory,
      recurse => true,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      require =>  File[ "${quartermaster::wwwroot}/microsoft" ],
    }
  }
  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/unattend"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/unattend":
      ensure  => directory,
      recurse => true,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      require =>  File[ "${quartermaster::wwwroot}/microsoft" ],
    }
  }
  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe":
      ensure  => directory,
      recurse => true,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      require =>  File[ "${quartermaster::wwwroot}/microsoft" ],
    }
  } 
  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/mnt.${w_arch}"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/mnt.${w_arch}":
      ensure  => directory,
      recurse => true,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      require =>  File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe"],
    }
  } 
  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot":
      ensure  => directory,
      recurse => true,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      require =>  File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe"],
    }
  } 
  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}"]) {
    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}":
      ensure  => link,
      owner   => 'www-data',
      group   => 'www-data',
      mode    => '0644',
      target  => "${quartermaster::wwwroot}/microsoft/mount/${name}",
      require =>  File[ "${quartermaster::wwwroot}/microsoft" ],
    }
  }

    exec { "copy-${w_flavor}-winpe.wim":
      command   => "/usr/bin/wget -cv http://${ipaddress}/microsoft/mount/${name}/sources/boot.wim -O ${w_arch}.wim",
      creates   => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/${w_arch}.wim",
      cwd       => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/",
      notify    => Exec["wimlib-imagex-mount-${name}"],
      require   => File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe"],
      logoutput => true,
    }
   file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/${w_arch}.wim":
     ensure => present, 
     require => Exec["copy-${w_flavor}-winpe.wim"]

   }
   
#  } 
#  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/etfsboot.com"]) {
#    exec { ${name}-etfsboot.com
#       command => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/etfsboot.com":
#      ensure  => directory,
#      recurse => true,
#      source  => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}/boot/etfsboot.com",
#      owner   => 'www-data',
#      group   => 'www-data',
#      mode    => '0644',
#      require =>  File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}"],
#    }
#  } 
  exec {"wimlib-imagex-mount-${name}":
    command     => "/usr/bin/wimlib-imagex mount ${w_arch}.wim 1 mnt.${w_arch}",
    cwd         => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe",
    refreshonly => true,
    require     => File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/mnt.${w_arch}",
                        "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/${w_arch}.wim"],
    notify      => Exec["wimlib-imagex-unmount-${name}"],
    logoutput   => true,
  } 
  exec {"wimlib-imagex-unmount-${name}":
    command     => "/usr/bin/wimlib-imagex unmount mnt.${w_arch}",
    cwd         => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe",
    refreshonly => true,
    require     => File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/mnt.${w_arch}"],
    logoutput   => true,
  } 


#  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/oscdimg.exe"]) {
#    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/oscdimg.exe":
#      ensure  => directory,
#      recurse => true,
#      source  => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}/pxe/mnt/Windows/Boot/PXE/oscdimg.exe",
#      owner   => 'www-data',
#      group   => 'www-data',
#      mode    => '0644',
#      require => [ Exec["wimlib-imagex-mount-${name}"], File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/oscdimg.exe"],
#    }
#  } 
#  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/pxeboot.com"]) {
#    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/pxeboot.com":
#      ensure  => file,
#      source  => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}/pxe/mnt/Windows/Boot/PXE/pxeboot.com",
#      owner   => 'www-data',
#      group   => 'www-data',
#      mode    => '0644',
#      require => [ Exec["wimlib-imagex-mount-${name}"], File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/mnt"]],
#      require => File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/mnt/Windows/Boot/PXE/pxeboot.com"],
#    }
#  }
#  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/pxeboot.0"]) {
#    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/pxeboot.0":
#      ensure  => directory,
#      recurse => true,
#      source  => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}/pxe/mnt/Windows/Boot/PXE/pxeboot.0",
#      owner   => 'www-data',
#      group   => 'www-data',
#      mode    => '0644',
#      require => File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/pxeboot.com"],
#    }
#  }
#  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/bootmgr.exe"]) {
#    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/bootmgr.exe":
#      ensure  => directory,
#      recurse => true,
#      source  => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}/pxe/mnt/Windows/Boot/PXE/bootmgr.exe",
#      owner   => 'www-data',
#      group   => 'www-data',
#      mode    => '0644',
#      require => File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/pxeboot.0"],
#    }
#  }
#  if ! defined (File["${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/boot.sdi"]) {
#    file { "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/boot.sdi":
#      ensure  => directory,
#      recurse => true,
#      source  => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/${w_arch}/pxe/mnt/Windows/Boot/PXE/boot.sdi",
#      owner   => 'www-data',
#      group   => 'www-data',
#      mode    => '0644',
#      notify  => Exec["wimlib-imagex-umount-${name}"],
#      require => File[ "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/pxe/Boot/bootmgr.exe"],
#    }
#  }

  file { "${name}-setup.cmd":
    ensure  => file,
    owner   => 'nobody',
    group   => 'nogroup',
    mode    => $quartermaster::exe_mode,
    path    => "${quartermaster::wwwroot}/microsoft/winpe/system/${name}.cmd",
    content => template("quartermaster/winpe/menu/default.erb"),
  }

  file { "unattend-${w_flavor}.xml":
    ensure  => file,
    owner   => 'nobody',
    group   => 'nogroup',
    mode    => $quartermaster::exe_mode,
    path    => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/unattend/${w_flavor}.xml",
    content => template('quartermaster/autoinst/unattend.erb'),
  }
  file { "unattend-${w_flavor}-cloudbase.xml":
    ensure  => file,
    owner   => 'nobody',
    group   => 'nogroup',
    mode    => $quartermaster::exe_mode,
    path    => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/unattend/${w_flavor}-cloudbase.xml",
    content => template('quartermaster/autoinst/Cloudbase.erb'),
  }
  file { "unattend-${w_flavor}-compute.xml":
    ensure  => file,
    owner   => 'nobody',
    group   => 'nogroup',
    mode    => $quartermaster::exe_mode,
    path    => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/unattend/${w_flavor}-compute.xml",
    content => template('quartermaster/autoinst/compute.erb'),
  }
#  if $w_distro == 'windows'{
#    file { "${name}-core-unattend.xml":
#      ensure  => file,
#      owner   => 'nobody',
#      group   => 'nogroup',
#      mode    => $quartermaster::exe_mode,
#      path    => "${quartermaster::wwwroot}/microsoft/${w_distro}/${w_release}/unattend/${w_flavor}.xml",
#      content => template('quartermaster/autoinst/unattend/core.erb'),
#    }
#  }
  
  concat::fragment{"winpe_system_cmd_a_init_${name}":
    target  => "${quartermaster::wwwroot}/microsoft/winpe/system/setup.cmd",
    content => template('quartermaster/winpe/menu/A_init.erb'),
    order   => 05,
  }
  concat::fragment{"winpe_system_cmd_b_init_${name}":
    target  => "${quartermaster::wwwroot}/microsoft/winpe/system/setup.cmd",
    content => template('quartermaster/winpe/menu/B_init.erb'),
    order   => 15,
  }
  concat::fragment{"winpe_system_cmd_c_init_${name}":
    target  => "${quartermaster::wwwroot}/microsoft/winpe/system/setup.cmd",
    content => template('quartermaster/winpe/menu/C_init.erb'),
    order   => 25,
  }

}
