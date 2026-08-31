{
  description = "Digital Signature Dev-Environment";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-26.05";

  outputs = { self, nixpkgs }:
    let
      javaVersion = 21;

      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
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

      devShells = forEachSystem ({ pkgs }: {
        default = pkgs.mkShell {
          packages = with pkgs; [
            jdk
            just
            kotlin
            maven
            mvnd
          ];

          shellHook = ''
            export CONFLUENCE_VERSION=$(just confluence-version)
            export CONFLUENCE_LICENSE=$(just confluence-license)
          '';
        };
      });
    };
}
