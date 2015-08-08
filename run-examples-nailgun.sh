# Needs nailgun server. Run run-nailgun-server.sh


# Crop three FCC contracts.  Output will be out/contract1_out.png etc.

ng "com.datapiece.Datapiece" -i pngs/contract1.png -b boxes_contract.json -o out/contract1_cropped.png --dpi 300
ng "com.datapiece.Datapiece" -i pngs/contract2.png -b boxes_contract.json -o out/contract2_cropped.png --dpi 300
ng "com.datapiece.Datapiece" -i pngs/contract3.png -b boxes_contract.json -o out/contract3_cropped.png --dpi 300
