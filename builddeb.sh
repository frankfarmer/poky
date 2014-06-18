#!/bin/bash

svn export --no-auth-cache --username frankfarmer --password c74a7acee5b9baaa81790c04d06dcc7bd66a445e https://github.com/drsnyder/poky/trunk /tmp/poky
cd /tmp/poky
lein clean && lein uberjar
#gem install fpm
fpm -x $0 -s dir -t deb -n huddler-poky -a all -v 1.2.0  --deb-user www-data --deb-group www-data --prefix /var/www/poky/releases/deb/ -C /tmp/poky/ .
