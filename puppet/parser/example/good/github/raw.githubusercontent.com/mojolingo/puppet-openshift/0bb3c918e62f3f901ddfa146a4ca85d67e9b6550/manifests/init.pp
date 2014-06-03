class openshift
{
  include lokkit::clear

  class { mongodb:
    enable_10gen => true,
  }

  class { ntp:
    ensure     => running,
    servers    => [ "time.apple.com iburst",
                    "pool.ntp.org iburst",
                    "clock.redhat.com iburst"],
    autoupdate => true,
  }

  yumrepo { "openshift-infrastructure":
    name => "openshift-infrastructure",
    baseurl => 'https://mirror.openshift.com/pub/origin-server/nightly/enterprise/2012-11-15/Infrastructure/x86_64/os/',
    enabled => 1,
    gpgcheck => 0,
  }

}
