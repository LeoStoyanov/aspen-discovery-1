#!/bin/sh
if [ -z "$1" ]
  then
    echo "Please provide the server name to update as the first argument."
    exit 1
fi

echo "Creating centralized suggest Solr core for $1"

# Copy the suggest core configuration if it doesn't exist
if [ ! -d "/data/aspen-discovery/$1/solr7/suggest" ]; then
  echo "Copying suggest core configuration..."
  cp -r /usr/local/aspen-discovery/data_dir_setup/solr7/suggest /data/aspen-discovery/"$1"/solr7/

  # Create the data directory
  mkdir -p /data/aspen-discovery/"$1"/solr7/suggest/data

  # Set proper ownership
  chown -R solr:aspen /data/aspen-discovery/"$1"/solr7/suggest

  echo "Suggest core created successfully"
else
  echo "Suggest core already exists, skipping creation"
fi

echo "Restarting Solr to load the new suggest core..."
/usr/local/aspen-discovery/sites/"$1"/"$1".sh restart

echo "Upgrade complete for $1"
