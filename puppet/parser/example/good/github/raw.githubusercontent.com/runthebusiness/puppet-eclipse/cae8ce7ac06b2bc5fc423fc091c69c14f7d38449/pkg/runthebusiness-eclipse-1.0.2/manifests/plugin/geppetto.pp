# Class: eclipse::plugin::geppetto
#
# This class installs eclipse geppetto plugin set
#
# Parameters:
#  - pluginrepositories: repositories of plugins to search in (Default: 'http://download.cloudsmith.com/geppetto/updates')
#  - suppresserrors: whether or not to supress errors when adding the plugin (Default: false)

# Sample Usage:

# 
define eclipse::plugin::geppetto(
	$pluginrepositories='http://download.cloudsmith.com/geppetto/updates',
  $suppresserrors=false,
) {
  ::eclipse::plugin{"eclipseinstallgeppetto":
    pluginrepositories=>$pluginrepositories,
    pluginius=>['org.cloudsmith.geppetto.feature.group'],
    suppresserrors=>$suppresserrors
  }
}
