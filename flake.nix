{
  description = "Confluence-Multitenancy Dev-Envirionment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/release-25.05";
  };

  outputs = { self, nixpkgs }:
    let
      javaVersion = 17;

      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
        pkgs = import nixpkgs { inherit system; overlays = [ self.overlays.default ]; };
      });
    in
    {
      overlays.default =
        final: prev: rec {
          jdk = prev."jdk${toString javaVersion}";
          maven = prev.maven.override { jdk_headless = jdk; };
          kotlin = prev.kotlin.override { jre = jdk; };
        };

      devShells = forEachSupportedSystem ({ pkgs }: {
        default = pkgs.mkShell {

          packages = with pkgs; [
            just
            jdk
            kotlin
            maven
          ];
        };
      });
    };
}
