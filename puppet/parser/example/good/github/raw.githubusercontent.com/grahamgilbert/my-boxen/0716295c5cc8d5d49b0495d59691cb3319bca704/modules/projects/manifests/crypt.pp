class projects::crypt (
	$my_homedir   = $people::grahamgilbert::params::my_homedir,
  	$my_sourcedir = $people::grahamgilbert::params::my_sourcedir,
  	$my_username  = $people::grahamgilbert::params::my_username
	){
	
	boxen::project { 'crypt':
		dir		=>	"${my_sourcedir}/Mine/crypt",
		source	=>	'grahamgilbert/Crypt',
	}
}