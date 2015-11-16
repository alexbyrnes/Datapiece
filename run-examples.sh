
# Output single horizontally-arranged image and the JSON 
# bounding boxes found.
./datapiece -i pngs/contract2.png -b boxes_contract.json -o out/contract2_one_line.png --dpi 300 --jsonout out/contract2.json

# Output series of images named out/contract2_<field>.png with --split. Clean a 2 pixel border around each output image.
./datapiece -i pngs/contract2.png -b boxes_contract.json -o out/contract2_ --dpi 300 --split -B 2

# Output JSON without images with --findonly.
./datapiece -i pngs/contract2.png -b boxes_contract.json --dpi 300 --jsonout out/contract2_findonly.json --findonly


