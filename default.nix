{ pkgs ? import ./nixpkgs.nix }:

with pkgs;

let
  name = "orco";

  metals-emacs = stdenv.mkDerivation rec {
    name = "metals-emacs";
    version = "0.8.0";

    dep-name = "org.scalameta:metals_2.12:${version}";

    src = stdenv.mkDerivation {
      name = "metals-deps";

      buildCommand = ''
        mkdir -p $out/share/deps
        export COURSIER_CACHE=$out/share/deps
        coursier fetch ${dep-name}
      '';

      outputHashAlgo = "sha256";
      outputHashMode = "recursive";
      outputHash = "0r95dr1zhzpaqsgf6bfs02wq9dnqmf3kdpk2h5dvb29c83h87a44";

      nativeBuildInputs = [ coursier ];
    };

    nativeBuildInputs = [ coursier makeWrapper ];

    buildCommand = ''
      mkdir -p $out/bin
      export COURSIER_CACHE=$src/share/deps
      coursier bootstrap \
      --mode offline \
      --java-opt -Xss4m   \
      --java-opt -Xms100m   \
      --java-opt -Dmetals.client=emacs \
      ${dep-name} \
      -r bintray:scalacenter/releases \
      -r sonatype:snapshots \
      -o $out/bin/${name}

      patchShebangs $out/bin/${name}
      wrapProgram $out/bin/${name} --prefix PATH ":" ${jre}/bin
    '';
  };

  drv = {};

  shell = mkShell {
    buildInputs = [ sbt coursier metals-emacs ];
  };

in drv // { inherit shell; }
