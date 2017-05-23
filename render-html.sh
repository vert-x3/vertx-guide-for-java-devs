#!/bin/sh
bundle exec asciidoctor \
    -b html \
    -o guide.html \
    -a icons=font \
    -a source-highlighter=highlight.js \
    -a data-uri \
    -a toc=left \
    -a toclevels=4 \
    -a sectnums \
    README.adoc
