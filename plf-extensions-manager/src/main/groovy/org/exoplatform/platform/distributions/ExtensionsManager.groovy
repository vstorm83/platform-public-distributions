package org.exoplatform.platform.distributions

import static org.fusesource.jansi.Ansi.ansi

/**
 * Copyright (C) 2003-2013 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

/**
 * Command line utility to manage Platform extensions.
 */



try {

// Initialize logging system
  Logging.initialize()
// And display header
  Logging.displayHeader()
// Parse command line parameters and fill settings with user inputs
  if (!CLI.initialize(args)) {
    Logging.dispose()
    System.exit 1
  } else if (Settings.Action == Settings.Action.HELP) {
    Logging.dispose()
    System.exit 0
  }
// Validate execution settings
  Settings.instance.validate()

  def List<Extension> extensions = new ArrayList<Extension>()
  def catalog
  Logging.logWithStatus("Reading local add-ons list...") {
    catalog = Settings.instance.localExtensionsCatalog
  }
  Logging.logWithStatus("Loading add-ons...") {
    extensions.addAll(Extension.parseJSONExtensionsList(catalog))
  }
  Logging.logWithStatus("Downloading central add-ons list...") {
    catalog = Settings.instance.centralCatalog
  }
  Logging.logWithStatus("Loading add-ons...") {
    extensions.addAll(Extension.parseJSONExtensionsList(catalog))
  }

  switch (Settings.instance.action) {
    case Settings.Action.LIST:
      println ansi().render("\n@|bold Available add-ons:|@\n")
      extensions.findAll { it.isStable() || Settings.instance.snapshots }.groupBy { it.id }.each {
        Extension anExtension = it.value.first()
        printf(ansi().render("+ @|bold,yellow %-${extensions.id*.size().max()}s|@ : @|bold %s|@, %s\n").toString(), anExtension.id, anExtension.name, anExtension.description)
        printf(ansi().render("     Available Version(s) : @|bold,yellow %-${extensions.version*.size().max()}s|@ \n\n").toString(), ansi().render(it.value.collect { "@|yellow ${it.version}|@" }.join(', ')))
      }
      println ansi().render("""
  To have more details about an add-on:
    ${CLI.getScriptName()} --info <@|yellow add-on|@>
  To install an add-on:
    ${CLI.getScriptName()} --install <@|yellow add-on|@>
  """).toString()
      break
    case Settings.Action.INSTALL:
      def extensionList = extensions.findAll { (it.isStable() || Settings.instance.snapshots) && Settings.instance.extensionId.equals(it.id) }
      if (extensionList.size() == 0) {
        Logging.logWithStatusKO("No add-on with identifier ${Settings.instance.extensionId} found")
        break
      }
      def extension = extensionList.first();
      extension.install()
      break
    case Settings.Action.UNINSTALL:
      def statusFile = new File(Settings.instance.extensionsDirectory, "${Settings.instance.extensionId}.status")
      if (statusFile.exists()) {
        def extension
        Logging.logWithStatus("Loading extension details...") {
          extension = Extension.parseJSONExtension(statusFile.text);
        }
        extension.uninstall()
      } else {
        Logging.logWithStatusKO("Add-on not installed. Exiting.")
      }
      break
    default:
      Logging.displayMsgError("Unsupported operation.")
      Logging.dispose()
      System.exit 1
  }
} catch (Exception e) {
  System.exit 1
} finally {
  Logging.dispose()
}

System.exit 0