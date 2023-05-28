#!/usr/bin/env python
# -*- coding: utf-8 -*-
import copy
import itertools
import json

from model import KeyPointClassifier


def main(hand_landmarks, width, height):
    keypoint_classifier = KeyPointClassifier()
        #  ####################################################################
    if hand_landmarks is not None:
        landmark_json = json.loads(hand_landmarks)
        # Landmark calculation
        landmark_list = calc_landmark_list(width, height, landmark_json)

        # Conversion to relative coordinates / normalized coordinates
        pre_processed_landmark_list = pre_process_landmark(
            landmark_list)

        # Hand sign classification
        hand_sign_id = keypoint_classifier(pre_processed_landmark_list)
        return hand_sign_id
    else: 
        return -1

def calc_landmark_list(width, height, landmarks):

    landmark_point = []

    # Keypoint
    for landmark in landmarks:
        landmark_x = min(int(landmark['x_'] * width), width - 1)
        landmark_y = min(int(landmark['y_'] * height), height - 1)
        # landmark_z = landmark.z

        landmark_point.append([landmark_x, landmark_y])

    return landmark_point


def pre_process_landmark(landmark_list):
    temp_landmark_list = copy.deepcopy(landmark_list)

    # Convert to relative coordinates
    base_x, base_y = 0, 0
    for index, landmark_point in enumerate(temp_landmark_list):
        if index == 0:
            base_x, base_y = landmark_point[0], landmark_point[1]

        temp_landmark_list[index][0] = temp_landmark_list[index][0] - base_x
        temp_landmark_list[index][1] = temp_landmark_list[index][1] - base_y

    # Convert to a one-dimensional list
    temp_landmark_list = list(
        itertools.chain.from_iterable(temp_landmark_list))

    # Normalization
    max_value = max(list(map(abs, temp_landmark_list)))

    def normalize_(n):
        return n / max_value

    temp_landmark_list = list(map(normalize_, temp_landmark_list))

    return temp_landmark_list

if __name__ == '__main__':
    main()
