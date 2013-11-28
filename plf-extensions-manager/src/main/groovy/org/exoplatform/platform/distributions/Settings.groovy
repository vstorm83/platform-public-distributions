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
 * Settings management
 */
class Settings {
  static final instance = new Settings()

  def private Properties props = null;
  def File productHome = new File(System.getProperty("product.home"))
  def File librariesDir = new File(productHome, System.getProperty("platform.libraries.path"))
  def File webappsDir = new File(productHome, System.getProperty("platform.webapps.path"))
  def File extensionsDirectory = new File(productHome, "extensions")
  def Action action
  def String extensionId
  def boolean verbose = false
  def boolean force = false
  def boolean snapshots = false

  private Settings() {
  }

  enum Action {
    LIST, INSTALL, UNINSTALL, HELP
  }

  private Properties getProps() {
    if (props == null) {
      props = new Properties()
      InputStream inputStream = getClass().getClassLoader().getResourceAsStream("org/exoplatform/platform/distributions/settings.properties")

      if (inputStream == null) {
        println "Property file settings.properties not found in the classpath"
        System.exit 1
      }
      try {
        props.load(inputStream)
      } finally {
        try {
          inputStream.close()
        } catch (Exception e) {
        }
      }
    }
    return props;
  }

  boolean validate() {
    if (!System.getProperty("product.home")) {
      println 'error: Erroneous setup, system property product.home not defined.'
      System.exit 1
    }
    if (!productHome.isDirectory()) {
      println "error: Erroneous setup, product home directory (${productHome}) is invalid."
      System.exit 1
    }
    if (!extensionsDirectory.isDirectory()) {
      println "error: Erroneous setup, add-ons directory (${extensionsDirectory}) is invalid."
      System.exit 1
    }
    if (!System.getProperty("platform.libraries.path")) {
      println 'error: Erroneous setup, system property platform.libraries.path not defined.'
      System.exit 1
    }
    if (!librariesDir.isDirectory()) {
      println "error: Erroneous setup, platform libraries directory (${librariesDir}) is invalid."
      System.exit 1
    }
    if (!System.getProperty("platform.webapps.path")) {
      println 'error: Erroneous setup, system property platform.webapps.path not defined.'
      System.exit 1
    }
    if (!webappsDir.isDirectory()) {
      println "error: Erroneous setup, platform web applications directory (${webappsDir}) is invalid."
      System.exit 1
    }
  }

  String getVersion() {
    return getProps().version
  }

  String getLocalExtensionsCatalogFilename() {
    return getProps().localExtensionsCatalogFilename
  }


  File getLocalExtensionsCatalogFile() {
    return new File(extensionsDirectory, getLocalExtensionsCatalogFilename())
  }

  URL getCentralCatalogUrl() {
    return new URL(getProps().centralCatalogUrl)
  }

  String getLocalExtensionsCatalog() {
    return getLocalExtensionsCatalogFile().text
  }

  String getCentralCatalog() {
    return getCentralCatalogUrl().text
  }

}
