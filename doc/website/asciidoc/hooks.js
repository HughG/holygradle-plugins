/*
 * This file is partly based on from "asciidoc.js", which is licensed as follows.
 */

/* Author: Mihai Bazon, September 2002
 * http://students.infoiasi.ro/~mishoo
 *
 * Table Of Content generator
 * Version: 0.4
 *
 * Feel free to use this script under the terms of the GNU General Public
 * License, as long as you do not remove or alter this notice.
 */

 /* modified by Troy D. Hanson, September 2006. License: GPL */
 /* modified by Stuart Rackham, 2006, 2009. License: GPL */

var asciidocHooks = {

    install: function() {
        var timerId;

        function reinstallTabs() {
            $( "#multi-code" ).tabs();
        }

        function addLinkElem(elem, stylesheetType, stylesheetPath) {
            var linkElem = elem.ownerDocument.createElementNS("http://www.w3.org/1999/xhtml", "link");
            linkElem.setAttribute("href", stylesheetPath);
            linkElem.setAttribute("type", stylesheetType);
            linkElem.setAttribute("rel", "stylesheet");
            elem.appendChild(linkElem);
        }

        function reinstallSvgStyle() {
            // Adapted from http://stackoverflow.com/questions/4906148/how-to-apply-a-style-to-an-embedded-svg
            var svgs = $( "object[type='image/svg+xml']" );
            svgs.each(function (index, elem) {
                if (elem.addEventListener) {
                    elem.addEventListener("load", function() {
                        var svgDoc = elem.contentDocument;
                        // Note that we can't use jQuery on the svgDoc, as far as I know, because it's a separate doc.
                        var svgElem = svgDoc.getElementsByTagName("svg")[0]
                        addLinkElem(svgElem, "text/css", "asciidoc/svg.css");
                        addLinkElem(svgElem, "text/css", "local/asciidoc/svg.css");
                    }, false);
                } else {
                    // IE 8 and below.
                    elem.attachEvent("onload", function() {
                        var svgDoc = elem.contentDocument;
                        // Note that we can't use jQuery on the svgDoc, as far as I know, because it's a separate doc.
                        var svgElem = svgDoc.getElementsByTagName("svg")[0]
                        addLinkElem(svgElem, "text/css", "asciidoc/svg.css");
                        addLinkElem(svgElem, "text/css", "local/asciidoc/svg.css");
                    });
                }
            });
        }

        function reinstall() {
            reinstallTabs();
            reinstallSvgStyle();
        }

        $(document).ready(function() {
            asciidoc.activate();
            reinstall();
        });
    }

}
asciidocHooks.install();
