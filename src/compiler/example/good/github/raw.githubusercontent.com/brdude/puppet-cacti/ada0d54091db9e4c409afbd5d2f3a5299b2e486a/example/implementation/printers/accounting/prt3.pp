class cacti::implementation::printers::accounting::prt3 { 

  cacti_add_device {
    'Accounting prt3':
      ip          => '192.168.1.22',
      template    => 'SNMP Printer',
      require     => Cacti::Import_templates['cacti_host_template_snmp_printer.xml'];
  }

  cacti_add_tree {
    'Accounting prt3':
      type       => 'node',
      tree       => 'Printers',
      nodetype   => 'header',
      parentnode => 'Accounting',
      require    => Cacti_add_tree['Accounting'];
  }

  cacti_add_graph {
    'Accounting prt3 - Total printed pages':
      device        => 'Accounting prt3',
      graphtype     => 'cg',
      graphtemplate => 'Host MIB - Total printed pages',
      require       => Cacti_add_device['Accounting prt3'];
  }

  cacti_add_tree {
    'Accounting prt3 - Total printed pages':
      type       => 'node',
      tree       => 'Printers',
      nodetype   => 'graph',
      parentnode => 'Accounting prt3',
      require    => [Cacti_add_tree['Accounting prt3'], Cacti_add_graph['Accounting prt3 - Total printed pages']];
  }

  cacti_add_graph {
    'Accounting prt3 - Used Level - Black Toner':
      device        => 'Accounting prt3',
      graphtype     => 'ds',
      graphtemplate => 'Host MIB - Available Printer Supply',
      snmpquery     => 'SNMP - Get Printer Supply',
      snmpquerytype => 'Available Printer Supply',
      snmpfield     => 'prtMarkersSupplyDesc',
      snmpvalue     => 'Black Toner',
      require       => Cacti_add_device['Accounting prt3'];
  }

  cacti_add_tree {
    'Accounting prt3 - Used Level - Black Toner':
      type       => 'node',
      tree       => 'Printers',
      nodetype   => 'graph',
      parentnode => 'Accounting prt3',
      require    => [Cacti_add_tree['Accounting prt3'], Cacti_add_graph['Accounting prt3 - Used Level - Black Toner']];
  }

  cacti_add_graph {
    'Accounting prt3 - Used Level - Waste Toner':
      device        => 'Accounting prt3',
      graphtype     => 'ds',
      graphtemplate => 'Host MIB - Available Printer Supply',
      snmpquery     => 'SNMP - Get Printer Supply',
      snmpquerytype => 'Available Printer Supply',
      snmpfield     => 'prtMarkersSupplyDesc',
      snmpvalue     => 'Waste Toner',
      require       => Cacti_add_device['Accounting prt3'];
  }
    
  cacti_add_tree {  
    'Accounting prt3 - Used Level - Waste Toner':
      type       => 'node',
      tree       => 'Printers',
      nodetype   => 'graph',
      parentnode => 'Accounting prt3',
      require    => [Cacti_add_tree['Accounting prt3'], Cacti_add_graph['Accounting prt3 - Used Level - Waste Toner']];
  }  

  cacti_add_graph {
    'Accounting prt3 - Traffic - IBM emacEnd dri':
      device        => 'Accounting prt3',
      graphtype     => 'ds',
      graphtemplate => 'Interface - Traffic (bits/sec)',
      snmpquery     => 'SNMP - Interface Statistics',
      snmpquerytype => 'In/Out Bits',
      snmpfield     => 'ifDescr',
      snmpvalue     => 'IBM emacEnd driver0',
      require       => Cacti_add_device['Accounting prt3'];
  }
    
  cacti_add_tree {  
    'Accounting prt3 - Traffic - IBM emacEnd dri':
      type       => 'node',
      tree       => 'Printers',
      nodetype   => 'graph',
      parentnode => 'Accounting prt3',
      require    => [Cacti_add_tree['Accounting prt3'], Cacti_add_graph['Accounting prt3 - Traffic - IBM emacEnd dri']];
  }

}
