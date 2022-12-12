#!/bin/bash

if [[ ! -f $(basename "${0}") || ! -d "../scripts" ]];
then
    echo "Error - run this script from the tools/scripts dir."
    exit 1
fi

DISTFILE=tools/dist.conf/distExcludes.txt
cd ../..
> "${DISTFILE}"
# ls -d tools/java/libs/* >> "${DISTFILE}"
echo >> "${DISTFILE}" <<EOF
tools/cldr-unittest/build
tools/java/target
EOF
for item in $(git ls-files -o);
do
    if [[ -d "${item}" ]];
    then
        echo "${item}/" >> "${DISTFILE}"
    else
        echo "${item}" >> "${DISTFILE}"
    fi
done

echo "# updated ${DISTFILE}"
