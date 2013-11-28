import groovy.json.JsonSlurper
import groovy.json.StreamingJsonBuilder
import groovy.util.slurpersupport.GPathResult
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil

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
@groovy.transform.Canonical
class Extension {

  def String id
  def String version
  def String name
  def String description
  def String releaseDate
  def String sourceUrl
  def String screenshotUrl
  def String thumbnailUrl
  def String documentationUrl
  def String downloadUrl
  def String vendor
  def String license
  def List<String> supportedDistributions
  def List<String> supportedApplicationServers
  def List<String> installedLibraries
  def List<String> installedWebapps

  static Extension fromJSON(anExtension) {
    def extensionObj = new Extension(
        anExtension.id ? anExtension.id : 'N/A',
        anExtension.version ? anExtension.version : 'N/A');
    extensionObj.name = anExtension.name ? anExtension.name : 'N/A'
    extensionObj.description = anExtension.description ? anExtension.description : 'N/A'
    extensionObj.releaseDate = anExtension.releaseDate ? anExtension.releaseDate : 'N/A'
    extensionObj.sourceUrl = anExtension.sourceUrl ? anExtension.sourceUrl : 'N/A'
    extensionObj.screenshotUrl = anExtension.screenshotUrl ? anExtension.screenshotUrl : 'N/A'
    extensionObj.thumbnailUrl = anExtension.thumbnailUrl ? anExtension.thumbnailUrl : 'N/A'
    extensionObj.documentationUrl = anExtension.documentationUrl ? anExtension.documentationUrl : 'N/A'
    extensionObj.downloadUrl = anExtension.downloadUrl ? anExtension.downloadUrl : 'N/A'
    extensionObj.vendor = anExtension.vendor ? anExtension.vendor : 'N/A'
    extensionObj.license = anExtension.license ? anExtension.license : 'N/A'
    if (anExtension.supportedDistributions instanceof String) {
      extensionObj.supportedDistributions = anExtension.supportedDistributions.split(',')
    } else {
      extensionObj.supportedDistributions = anExtension.supportedDistributions ? anExtension.supportedDistributions : []
    }
    if (anExtension.supportedApplicationServers instanceof String) {
      extensionObj.supportedApplicationServers = anExtension.supportedApplicationServers.split(',')
    } else {
      extensionObj.supportedApplicationServers = anExtension.supportedApplicationServers ? anExtension.supportedApplicationServers : []
    }
    extensionObj.installedLibraries = anExtension.installedLibraries ? anExtension.installedLibraries : []
    extensionObj.installedWebapps = anExtension.installedWebapps ? anExtension.installedWebapps : []
    // TODO : Add some validations here
    return extensionObj
  }

  static List<Extension> parseJSONExtensionsList(String text) {
    List<Extension> extensionsList = new ArrayList<Extension>();
    new JsonSlurper().parseText(text).each { anExtension ->
      extensionsList.add(fromJSON(anExtension))
    }
    return extensionsList
  }

  static Extension parseJSONExtension(String text) {
    return fromJSON(new JsonSlurper().parseText(text))
  }

  Extension(String id, String version) {
    this.id = id
    this.version = version
  }

  File getLocalArchive() {
    return new File(Settings.instance.extensionsDirectory, id + "-" + version + ".zip")
  }

  String getExtensionStatusFilename() {
    return "${id}.status"
  }

  File getExtensionStatusFile() {
    return new File(Settings.instance.extensionsDirectory, extensionStatusFilename)
  }

  boolean isInstalled() {
    return extensionStatusFile.exists()
  }

  boolean isStable() {
    return !(this.version =~ '.*SNAPSHOT$')
  }

  def install() {
    if (installed) {
      if (!Settings.instance.force) {
        Logging.logWithStatusKO("Add-on already installed. Use --force to enforce to override it")
        return
      } else {
        Extension oldExtension = Extension.parseJSONExtension(extensionStatusFile.text);
        oldExtension.uninstall()
      }
    }
    Logging.displayMsgInfo("Installing @|yellow ${name} ${version}|@ ...")
    if (!localArchive.exists() || Settings.instance.force) {
      // Let's download it
      if (downloadUrl.startsWith("http")) {
        Logging.logWithStatus("Downloading add-on ${name} ${version} ...") {
          MiscUtils.downloadFile(downloadUrl, localArchive)
        }
      } else if (downloadUrl.startsWith("file://")) {
        Logging.logWithStatus("Copying add-on ${name} ${version} ...") {
          MiscUtils.copyFile(new File(Settings.instance.extensionsDirectory, downloadUrl.replaceAll("file://", "")), localArchive)
        }
      } else {
        throw new Exception("Invalid or not supported download URL : ${downloadUrl}")
      }
    }
    this.installedLibraries = MiscUtils.flatExtractFromZip(localArchive, Settings.instance.librariesDir, '^.*jar$')
    this.installedWebapps = MiscUtils.flatExtractFromZip(localArchive, Settings.instance.webappsDir, '^.*war$')
    // Update application.xml if it exists
    def applicationDescriptor = new File(Settings.instance.webappsDir, "META-INF/application.xml")
    if (applicationDescriptor.exists()) {
      processFileInplace(applicationDescriptor) { text ->
        application = new XmlSlurper(false, false).parseText(text)
        installedWebapps.each { file ->
          def webArchive = file
          def webContext = file.substring(0, file.name.length() - 4)
          Logging.logWithStatus("Adding context declaration /${webContext} for ${webArchive} in application.xml ... ") {
            application.depthFirst().findAll { (it.name() == 'module') && (it.'web'.'web-uri'.text() == webArchive) }.each { node ->
              // remove existing node
              node.replaceNode {}
            }
            application."initialize-in-order" + {
              module {
                web {
                  'web-uri'(webArchive)
                  'context-root'(webContext)
                }
              }
            }
          }
          serializeXml(application)
        }
      }
    }
    Logging.logWithStatus("Recording installation details into ${extensionStatusFilename} ... ") {
      new FileWriter(extensionStatusFile).withWriter { w ->
        def builder = new StreamingJsonBuilder(w)
        builder(
            id: id,
            version: version,
            name: name,
            description: description,
            releaseDate: releaseDate,
            sourceUrl: sourceUrl,
            screenshotUrl: screenshotUrl,
            thumbnailUrl: thumbnailUrl,
            documentationUrl: documentationUrl,
            downloadUrl: downloadUrl,
            vendor: vendor,
            license: license,
            supportedDistributions: supportedDistributions,
            supportedApplicationServers: supportedApplicationServers,
            installedLibraries: installedLibraries,
            installedWebapps: installedWebapps
        )
      }
    }
    Logging.logWithStatusOK("Add-on ${name} ${version} installed.")
  }

  void uninstall() {
    Logging.displayMsgInfo("Uninstalling @|yellow ${name} ${version}|@ ...")

    installedLibraries.each {
      library ->
        def File fileToDelete = new File(Settings.instance.librariesDir, library)
        if (!fileToDelete.exists()) {
          Logging.displayMsgWarn("No library ${library} to delete")
        } else {
          Logging.logWithStatus("Deleting library ${library} ... ") {
            fileToDelete.delete()
            assert !fileToDelete.exists()
          }
        }
    }

    // Update application.xml if it exists
    def applicationDescriptor = new File(Settings.instance.webappsDir, "META-INF/application.xml")

    installedWebapps.each {
      webapp ->
        def File fileToDelete = new File(Settings.instance.webappsDir, webapp)
        if (!fileToDelete.exists()) {
          Logging.displayMsgWarn("No web application ${webapp} to delete")
        } else {
          Logging.logWithStatus("Deleting web application ${webapp} ... ") {
            fileToDelete.delete()
            assert !fileToDelete.exists()
          }
        }
        if (applicationDescriptor.exists()) {
          Logging.logWithStatus("Adding context declaration /${webContext} for ${webapp} in application.xml ...") {
            processFileInplace(applicationDescriptor) { text ->
              application = new XmlSlurper(false, false).parseText(text)
              def webArchive = webapp
              def webContext = webapp.substring(0, file.name.length() - 4)
              application.depthFirst().findAll { (it.name() == 'module') && (it.'web'.'web-uri'.text() == webArchive) }.each { node ->
                print "Removing context declaration /${webContext} for ${webArchive} in application.xml ... "
                // remove existing node
                node.replaceNode {}
                println ansi().render('[@|green OK|@]')
              }
              serializeXml(application)
            }
          }
        }
    }
    Logging.logWithStatus("Deleting installation details ${extensionStatusFilename} ... ") {
      extensionStatusFile.delete()
      assert !extensionStatusFile.exists()
    }
    Logging.logWithStatusOK("Add-on ${name} ${version} uninstalled.")
  }

  private String serializeXml(GPathResult xml) {
    XmlUtil.serialize(new StreamingMarkupBuilder().bind {
      mkp.yield xml
    })
  }

  private processFileInplace(file, Closure processText) {
    def text = file.text
    file.write(processText(text))
  }

}

