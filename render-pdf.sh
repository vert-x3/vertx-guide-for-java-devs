#!/bin/sh
bundle exec asciidoctor-pdf \
    -o guide.pdf \
    -a icons=font \
    -a source-highlighter=rouge \
    -a autofit-option \
    -a toc \
    -a toclevels=4 \
    -a sectnums \
    README.adoc
