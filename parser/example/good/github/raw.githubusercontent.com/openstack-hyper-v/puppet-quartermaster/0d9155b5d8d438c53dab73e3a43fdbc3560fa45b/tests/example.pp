node ubuntu {
  class{'quartermaster':}
    # Ubuntu
    quartermaster::pxe{'ubuntu-12.04-amd64':}
    quartermaster::pxe{'ubuntu-12.10-amd64':}
    quartermaster::pxe{'ubuntu-13.04-amd64':}
    quartermaster::pxe{'ubuntu-13.10-amd64':}
    # Fedora
    quartermaster::pxe{"fedora-17-x86_64":}
    quartermaster::pxe{"fedora-16-i386":}
    # Centos
    quartermaster::pxe{"centos-6.3-x86_64":}
    # Scientific Linux
    quartermaster::pxe{"scientificlinux-6.3-x86_64":}
    # OpenSuSE
    quartermaster::pxe{"opensuse-12.2-x86_64":}
    # Debian
    quartermaster::pxe{"debian-stable-amd64":}
}
