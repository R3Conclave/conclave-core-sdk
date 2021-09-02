# Extracts the distribution zip in build/distributions corresponding to the current version
function extractCurrentDistribution {
    project_root=$(dirname "${BASH_SOURCE[0]}")
    distributions_dir="$project_root/build/distributions/"
    conclave_version=$(grep conclave_version versions.gradle | cut -d'=' -f2 | tr -d " " | tr -d "'")
    conclave_sdk_name="conclave-sdk-$conclave_version"
    unzip -o "$distributions_dir$conclave_sdk_name.zip" -d "$distributions_dir"
}
