package com.timejet.bio.timejet

import java.util.*
import javax.activation.CommandMap
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.activation.MailcapCommandMap
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class Mail() : javax.mail.Authenticator() {
    var _user: String? = null
    var _pass: String? = null

    var _to: Array<String>? = null
    var _from: String? = null

    var _port: String? = null
    var _sport: String? = null

    var _host: String? = null

    var _subject: String? = null
    // the getters and setters
    var body: String? = null

    var is_auth: Boolean = false

    var is_debuggable: Boolean = false

    var _multipart: Multipart? = null

    init {
        _host = "smtp.gmail.com" // default smtp server
        _port = "465" // default smtp port
        _sport = "465" // default socketfactory port

        _user = "" // username
        _pass = "" // password
        _from = "" // email sent from
        _subject = "" // email subject
        body = "" // email body

        is_debuggable = true // debug mode on or off - default off
        is_auth = true // smtp authentication - default on

        _multipart = MimeMultipart()

        // There is something wrong with MailCap, javamail can not find a
        // handler for the multipart/mixed part, so this bit needs to be added.
        val mc = CommandMap
                .getDefaultCommandMap() as MailcapCommandMap
        mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
        mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
        mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
        mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
        mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
        CommandMap.setDefaultCommandMap(mc)
    }

    constructor(user: String, pass: String) : this() {

        _user = user
        _pass = pass
    }

    @Throws(Exception::class)
    fun send(): Boolean {
        val props = _setProperties()

        if (_user != "" && _pass != "" && _to!!.size > 0
                && _from != "" && _subject != ""
                && body != "") {
            val session = Session.getInstance(props, this)

            val msg = MimeMessage(session)

            msg.setFrom(InternetAddress(_from!!))

            val addressTo = arrayOfNulls<InternetAddress>(_to!!.size)
            for (i in _to!!.indices) {
                addressTo[i] = InternetAddress(_to!![i])
            }
            msg.setRecipients(MimeMessage.RecipientType.TO, addressTo)

            msg.subject = _subject
            msg.sentDate = Date()

            // setup message body
            val messageBodyPart = MimeBodyPart()
            messageBodyPart.setText(body)
            _multipart!!.addBodyPart(messageBodyPart)

            msg.setHeader("X-Priority", "1")
            // Put parts in message
            msg.setContent(_multipart!!)

            // send email
            Transport.send(msg)

            return true
        } else {
            return false
        }
    }

    @Throws(Exception::class)
    fun addAttachment(filename: String) {
        val messageBodyPart = MimeBodyPart()
        val source = FileDataSource(filename)
        messageBodyPart.dataHandler = DataHandler(source)
        messageBodyPart.fileName = filename

        _multipart!!.addBodyPart(messageBodyPart)
    }

    public override fun getPasswordAuthentication(): PasswordAuthentication {
        return PasswordAuthentication(_user, _pass)
    }

    private fun _setProperties(): Properties {
        val props = Properties()
        props["mail.smtp.host"] = _host
        if (is_debuggable) {
            props["mail.debug"] = "true"
        }
        if (is_auth) {
            props["mail.smtp.auth"] = "true"
        }
        props["mail.smtp.port"] = _port
        props["mail.smtp.socketFactory.port"] = _sport
        props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        props["mail.smtp.socketFactory.fallback"] = "false"

        return props
    }
}