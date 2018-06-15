import cPickle as pickle

from numpy import *

train_set = ([array([26, 41, 16, 36, 8, 18, 13, 17, 31, 21, 0, 9, 15, 32, 38, 40, 6], dtype=int32),
 array([7, 33, 28, 16, 36, 23, 5, 12, 13, 39, 22, 2, 14, 19], dtype=int32),
 array([34, 35, 24, 10, 37, 21, 4], dtype=int32),
 array([29, 30, 27, 22, 20, 40, 3, 11, 36, 25, 1], dtype=int32)],
 [array([170, 171, 170, 172, 172, 172, 173, 174, 174, 174, 174, 174, 175, 176, 177, 177, 177], dtype=int32),
 array([170, 170, 171, 178, 179, 179, 179, 179, 173, 174, 174, 174, 174, 174], dtype=int32),
 array([180, 171, 175, 171, 173, 174, 174], dtype=int32),
 array([171, 170, 170, 172, 172, 181, 182, 182, 182, 182, 182], dtype=int32)],
 [array([0, 1, 0, 2, 2, 2, 3, 4, 4, 4, 4, 4, 5, 6, 7, 7, 7], dtype=int32),
 array([0, 0, 1, 8, 9, 9, 9, 9, 3, 4, 4, 4, 4, 4], dtype=int32),
 array([10, 1, 5, 1, 3, 4, 4], dtype=int32),
 array([1, 0, 0, 2, 2, 11, 12, 12, 12, 12, 12], dtype=int32)])

valid_set = ([array([34, 35, 24, 10, 37, 21, 4], dtype=int32)],
 [array([180, 171, 175, 171, 173, 174, 174], dtype=int32)],
 [array([10, 1, 5, 1, 3, 4, 4], dtype=int32)])

test_set = ([array([7, 33, 28, 16, 36, 23, 5, 12, 13, 39, 22, 2, 14, 19], dtype=int32)],
 [array([170, 170, 171, 178, 179, 179, 179, 179, 173, 174, 174, 174, 174, 174], dtype=int32)],
 [array([0, 0, 1, 8, 9, 9, 9, 9, 3, 4, 4, 4, 4, 4], dtype=int32)])

dicts = {'labels2idx': {'B-purpose': 11, 'B-associated-fact': 6, 'I-associated-fact': 7, 'B-accessory-determiner': 10, 'I-purpose': 12, 'I-differentia-quality': 2, 'I-differentia-event': 4, 'O': 5, 'B-origin-location': 8, 'B-differentia-quality': 0, 'B-supertype': 1, 'I-origin-location': 9, 'B-differentia-event': 3},
 'tables2idx': {'B-purpose': 181, 'B-associated-fact': 176, 'I-associated-fact': 177, 'B-accessory-determiner': 180, 'I-purpose': 182, 'I-differentia-quality': 172, 'I-differentia-event': 174, '<NOTABLE>': 175, 'B-origin-location': 178, 'B-differentia-quality': 170, 'B-supertype': 171, 'I-origin-location': 179, 'B-differentia-event': 173},
 'words2idx': {'minerals': 1, 'strong': 2, 'separate': 3, 'another': 4, 'United': 5, 'farmers': 6, 'medium-sized': 7, 'northern': 8, 'rodents': 9, 'layer': 10, 'out': 11, 'States': 12, 'that': 13, 'durable': 14, 'and': 15, 'of': 16, 'feeds': 17, 'hemisphere': 18, 'wood': 19, 'pan': 20, '<UNK>': 0, 'on': 21, 'a': 22, 'eastern': 23, 'or': 24, 'precious': 25, 'large': 26, 'in': 27, 'tree': 28, 'wash': 29, 'dirt': 30, 'chiefly': 31, 'is': 32, 'deciduous': 33, 'any': 34, 'stratum': 35, 'the': 36, 'superimposed': 37, 'beneficial': 38, 'yields': 39, 'to': 40, 'hawk': 41}}

data = train_set, valid_set, test_set, dicts

f = open('data_fold3.pkl', 'w')
pickle.dump(data, f)