#!/bin/sh
bundle exec asciidoctor -b html -o guide.html -a icons=font README.adoc
