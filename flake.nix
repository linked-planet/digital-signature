{
  description = "Confluence-Multitenancy Dev-Envirionment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/release-25.05";
  };

  outputs = { self, nixpkgs }:
    let
    javaVersion = 21;

      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f:
      nixpkgs.lib.genAttrs supportedSystems (system:
        let
          pkgs = import nixpkgs { inherit system; };
          jdk  = pkgs.jdk21;

          mvnWrapped = pkgs.writeShellScriptBin "mvn" ''
            #!/usr/bin/env sh
            # Kotlin looks for jmods/ under JAVA_HOME; Nix's jdk store root is a wrapper.
            export JAVA_HOME="${jdk.home}"
            export JDK21_HOME="${jdk.home}"
            # Compute project root (git if available; else current dir)
            root="$(${pkgs.git}/bin/git -C . rev-parse --show-toplevel 2>/dev/null || ${pkgs.coreutils}/bin/pwd -P)"
            # Allow override; otherwise use repo-local file
            tc="''${TOOLCHAINS_FILE:-$root/.mvn/maven-toolchains.xml}"
            exec ${pkgs.maven}/bin/mvn --toolchains "''${tc}" "$@"
          '';

        in f { inherit mvnWrapped pkgs jdk; }
      );
    in
    {
      overlays.default =
        final: prev: rec {
          jdk = prev."jdk${toString javaVersion}";
          maven = prev.maven.override { jdk_headless = jdk; };
          kotlin = prev.kotlin.override { jre = jdk; };
        };

      devShells = forEachSupportedSystem ({ mvnWrapped, pkgs, jdk }: {
        default = pkgs.mkShell {

          packages = with pkgs; [
            mvnWrapped
            just
            jdk
            kotlin
            maven
          ];

          shellHook = ''
            # Nix's jdk derivation is a wrapper; Kotlin/Maven need the real JAVA_HOME
            # (lib/openjdk with jmods/), not the store root. `jdk.home` is that path.
            export JAVA_HOME="${jdk.home}"
            export JDK21_HOME="${jdk.home}"
            echo JDK21_HOME=$JDK21_HOME

            echo ... Maven was wrapped to use the local .mvn/maven-toolchains.xml file...
          '';
        };
      });
    };
}
